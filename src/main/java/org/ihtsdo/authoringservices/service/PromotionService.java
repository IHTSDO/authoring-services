package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.ClassificationPrerequisiteResult;
import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.Context;
import org.ihtsdo.authoringservices.service.client.AuthoringAcceptanceGatewayClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClientFactory;
import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient.US_EN_LANG_REFSET;

@Service
public class PromotionService {

    private static final String PROMOTION_ERROR_STATUS = "Promotion Error";
    private static final String FAILED_STATUS = "Failed";
    private static final String REBASING_STATUS = "Rebasing";
    private static final String REBASED_WITH_CONFLICT_STATUS = "Rebased with conflicts";
    private static final String STOPPED_STATUS = "stopped";
    private static final String CONTENT_PROMOTION = "Content Promotion";
    private static final String TASK_PROMOTION_DISABLED_MSG = "Task promotion is disabled";
    private static final String PROJECT_PROMOTION_DISABLED_MSG = "Project promotion is disabled";
    private static final String UNKNOWN_PROMOTION_ERROR_MSG = "Promotion failed with an unknown error";
    private static final String CRITERIA_HAVE_BEEN_SIGNED_OFF_MSG = "Not all Acceptance Criteria have been signed off";

    public static final Pattern TAG_PATTERN = Pattern.compile("^.*\\((.*)\\)$");
    public static final String IS_A = "116680003";

    private final CacheService cacheService;
    private final TaskServiceFactory taskServiceFactory;
    private final ProjectServiceFactory projectServiceFactory;
    private final NotificationService notificationService;
    private final SnowstormRestClientFactory snowstormRestClientFactory;
    private final ContentRequestServiceClientFactory contentRequestServiceClientFactory;
    private final BranchService branchService;
    private final ReleaseNoteService releaseNoteService;
    private final SnowstormClassificationClient classificationService;
    private final ClassificationPrerequisiteService classificationPrerequisiteService;
    private final CrsBlockingStateService crsBlockingStateService;
    private final AuthoringAcceptanceGatewayClient aagClient;
    private final UiConfiguration uiConfiguration;

    private final Map<String, ProcessStatus> automateTaskPromotionStatus;
    private final Map<String, ProcessStatus> taskPromotionStatus;
    private final Map<String, ProcessStatus> projectPromotionStatus;
    private final ExecutorService executorService;
    private final LinkedBlockingQueue<AutomatePromoteProcess> autoPromoteBlockingQueue = new LinkedBlockingQueue<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PromotionService(CacheService cacheService, TaskServiceFactory taskServiceFactory,
            ProjectServiceFactory projectServiceFactory, NotificationService notificationService,
            SnowstormRestClientFactory snowstormRestClientFactory,
            ContentRequestServiceClientFactory contentRequestServiceClientFactory, BranchService branchService,
            ReleaseNoteService releaseNoteService, SnowstormClassificationClient classificationService,
            ClassificationPrerequisiteService classificationPrerequisiteService,
            CrsBlockingStateService crsBlockingStateService, AuthoringAcceptanceGatewayClient aagClient,
            UiConfiguration uiConfiguration) {
        this.cacheService = cacheService;
        this.taskServiceFactory = taskServiceFactory;
        this.projectServiceFactory = projectServiceFactory;
        this.notificationService = notificationService;
        this.snowstormRestClientFactory = snowstormRestClientFactory;
        this.contentRequestServiceClientFactory = contentRequestServiceClientFactory;
        this.branchService = branchService;
        this.releaseNoteService = releaseNoteService;
        this.classificationService = classificationService;
        this.classificationPrerequisiteService = classificationPrerequisiteService;
        this.crsBlockingStateService = crsBlockingStateService;
        this.aagClient = aagClient;
        this.uiConfiguration = uiConfiguration;
        this.automateTaskPromotionStatus = new HashMap<>();
        this.taskPromotionStatus = new HashMap<>();
        this.projectPromotionStatus = new HashMap<>();
        this.executorService = Executors.newCachedThreadPool();
    }

    public String requestConceptPromotion(String conceptId, boolean includeDependencies, String branchPath, CodeSystem codeSystem) throws BusinessServiceException {
        ContentRequestServiceClient contentRequestServiceClient = contentRequestServiceClientFactory.getClient(uiConfiguration.getEndpoints().get("crsEndpoint"));
        JSONObject request = constructCRSRequestBody(conceptId, branchPath, codeSystem, includeDependencies);
        try {
            return contentRequestServiceClient.createRequest(request);
        } catch (RestClientException e) {
            String error = String.format("Failed to request for a concept promotion. Error: %s", e.getMessage());
            throw new BusinessServiceException(error, e);
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 5_000)
    private void processAutoPromotionJobs() {
        try {
            AutomatePromoteProcess automatePromoteProcess = autoPromoteBlockingQueue.take();
            doAutomateTaskPromotion(automatePromoteProcess.getProjectKey(), automatePromoteProcess.getTaskKey(), automatePromoteProcess.getAuthentication());
        } catch (InterruptedException e) {
            logger.warn("Failed to take task auto-promotion job from the queue.", e);
            Thread.currentThread().interrupt();
        }
    }

    public void queueAutomateTaskPromotion(String projectKey, String taskKey) {
        AutomatePromoteProcess automatePromoteProcess = new AutomatePromoteProcess(SecurityContextHolder.getContext().getAuthentication(), projectKey, taskKey);

        try {
            ProcessStatus processStatus = new ProcessStatus("Queued", "");
            automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
            autoPromoteBlockingQueue.put(automatePromoteProcess);
        } catch (InterruptedException e) {
            ProcessStatus failedStatus = new ProcessStatus(FAILED_STATUS, e.getMessage());
            automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), failedStatus);
            Thread.currentThread().interrupt();
        }
    }

    public void doTaskPromotion(String projectKey, String taskKey, MergeRequest mergeRequest) throws BusinessServiceException {
        boolean useNew = taskServiceFactory.getInstance(true).exists(taskKey);
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        if (Boolean.TRUE.equals(project.isTaskPromotionDisabled())) {
            throw new BusinessServiceException(TASK_PROMOTION_DISABLED_MSG);
        }

        String taskBranchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String statusKey = parseKey(projectKey, taskKey);
        executorService.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            ProcessStatus taskProcessStatus = new ProcessStatus();
            try {
                taskProcessStatus.setStatus(REBASING_STATUS);
                taskProcessStatus.setMessage("Task is rebasing");
                taskPromotionStatus.put(statusKey, taskProcessStatus);
                Merge merge = branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), mergeRequest.getSourceReviewId());
                if (merge.getStatus() == Merge.Status.COMPLETED) {
                    taskServiceFactory.getInstance(useNew).stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
                    notificationService.queueNotification(SecurityUtil.getUsername(),
                            new Notification(projectKey, taskKey, EntityType.Promotion, "Task successfully promoted"));
                    taskProcessStatus.setStatus("Promotion Complete");
                    taskProcessStatus.setMessage("Task successfully promoted");
                    taskPromotionStatus.put(statusKey, taskProcessStatus);

                    org.ihtsdo.authoringservices.domain.User user = taskServiceFactory.getInstance(useNew).getUser(SecurityUtil.getUsername());
                    releaseNoteService.promoteTaskLineItems(branchService.getTaskBranchPathUsingCache(projectKey, taskKey), user.getDisplayName());

                    // clear Auto Promotion status if the task has been triggered the Automated Promotion
                    automateTaskPromotionStatus.remove(statusKey);
                } else if (merge.getStatus() == Merge.Status.CONFLICTS) {
                    processPromotionConflicts(merge, taskProcessStatus, taskPromotionStatus, statusKey);
                } else {
                    updatePromotionError(taskProcessStatus, getMergeErrorMessage(merge), taskPromotionStatus, statusKey);
                }
            } catch (Exception e) {
                handlePromotionException(e, taskProcessStatus, taskPromotionStatus, statusKey, "Task");
            }
        });
    }

    public void doProjectPromotion(String projectKey, MergeRequest mergeRequest) throws BusinessServiceException {
        boolean useNew = projectServiceFactory.getInstance(true).exists(projectKey);
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        if (Boolean.TRUE.equals(project.isProjectPromotionDisabled()) || Boolean.TRUE.equals(project.isProjectLocked())) {
            throw new BusinessServiceException(getProjectPromotionDisabledMessage(project));
        }
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        executorService.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            ProcessStatus projectProcessStatus = new ProcessStatus();
            try {
                projectProcessStatus.setStatus(REBASING_STATUS);
                projectProcessStatus.setMessage("Project is rebasing");
                projectPromotionStatus.put(projectKey, projectProcessStatus);
                String projectBranchPath = branchService.getProjectBranchPathUsingCache(projectKey);
                Merge merge = branchService.mergeBranchSync(projectBranchPath, PathHelper.getParentPath(projectBranchPath), mergeRequest.getSourceReviewId());
                if (merge.getStatus() == Merge.Status.COMPLETED) {
                    List<AuthoringTask> promotedTasks = taskServiceFactory.getInstance(useNew).getTasksByStatus(projectKey, TaskStatus.PROMOTED);
                    taskServiceFactory.getInstance(useNew).stateTransition(promotedTasks, TaskStatus.COMPLETED, projectKey);
                    notificationService.queueNotification(SecurityUtil.getUsername(),
                            new Notification(projectKey, null, EntityType.Promotion, "Project successfully promoted"));
                    projectProcessStatus.setStatus("Promotion Complete");
                    projectProcessStatus.setMessage("Project successfully promoted");
                    projectPromotionStatus.put(projectKey, projectProcessStatus);

                    releaseNoteService.promoteProjectLineItems(projectBranchPath);
                } else if (merge.getStatus() == Merge.Status.CONFLICTS) {
                    processPromotionConflicts(merge, projectProcessStatus, projectPromotionStatus, projectKey);
                } else {
                    updatePromotionError(projectProcessStatus, getMergeErrorMessage(merge), projectPromotionStatus, projectKey);
                }
            } catch (Exception e) {
                handlePromotionException(e, projectProcessStatus, projectPromotionStatus, projectKey, "Project");
            }
        });

    }

    private String getProjectPromotionDisabledMessage(AuthoringProject project) {
        if (Boolean.TRUE.equals(project.isProjectLocked())) {
            return PROJECT_PROMOTION_DISABLED_MSG + " due to project being locked";
        }
        return PROJECT_PROMOTION_DISABLED_MSG;
    }

    private void processPromotionConflicts(Merge merge, ProcessStatus processStatus, Map<String, ProcessStatus> statusMap, String statusKey) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonInString = mapper.writeValueAsString(merge);
            processStatus.setStatus(Merge.Status.CONFLICTS.name());
            processStatus.setMessage(jsonInString);
            statusMap.put(statusKey, processStatus);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize promotion conflicts for {}", statusKey, e);
            updatePromotionError(processStatus, "Promotion failed due to merge conflicts", statusMap, statusKey);
        }
    }

    private void updatePromotionError(ProcessStatus processStatus, String message, Map<String, ProcessStatus> statusMap, String statusKey) {
        processStatus.setStatus(PROMOTION_ERROR_STATUS);
        processStatus.setMessage(StringUtils.hasLength(message) ? message : UNKNOWN_PROMOTION_ERROR_MSG);
        statusMap.put(statusKey, processStatus);
    }

    private String getMergeErrorMessage(Merge merge) {
        if (merge == null) {
            return UNKNOWN_PROMOTION_ERROR_MSG;
        }
        ApiError apiError = merge.getApiError();
        if (apiError != null && StringUtils.hasLength(apiError.getMessage())) {
            return apiError.getMessage();
        }
        if (merge.getStatus() != null) {
            return String.format("Promotion failed with merge status %s", merge.getStatus());
        }
        return UNKNOWN_PROMOTION_ERROR_MSG;
    }

    private void handlePromotionException(Exception e, ProcessStatus processStatus, Map<String, ProcessStatus> statusMap, String statusKey, String promotionType) {
        logger.error("{} promotion failed for {}", promotionType, statusKey, e);
        String message = e.getMessage();
        if (!StringUtils.hasLength(message)) {
            message = String.format("%s promotion failed with an unknown error", promotionType);
        }
        updatePromotionError(processStatus, message, statusMap, statusKey);
    }

    private synchronized void doAutomateTaskPromotion(String projectKey, String taskKey, Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            logger.info("Beginning auto promotion of task {}", taskKey);
            boolean useNew = projectServiceFactory.getInstance(true).exists(projectKey);
            AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
            if (Boolean.TRUE.equals(project.isTaskPromotionDisabled())) {
                ProcessStatus status = new ProcessStatus(FAILED_STATUS, TASK_PROMOTION_DISABLED_MSG);
                automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
                logger.error(TASK_PROMOTION_DISABLED_MSG);
                return;
            }

            // Call rebase process
            String rebaseStatus = this.autoRebaseTask(projectKey, taskKey, useNew);
            if (null != rebaseStatus && rebaseStatus.equals(STOPPED_STATUS)) {
                return;
            }

            // Call classification process
            Classification classification = this.autoClassificationTask(projectKey, taskKey);
            if (classification != null
                    && !classification.hasInferredRelationshipChangesFound()
                    && !classification.hasEquivalentConceptsFound()) {

                // Call promote process
                Merge merge = this.autoPromoteTask(projectKey, taskKey);
                if (merge.getStatus() == Merge.Status.COMPLETED) {
                    notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Promotion, "Automated promotion completed"));
                    ProcessStatus status = new ProcessStatus("Completed", "");
                    automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
                    taskServiceFactory.getInstance(useNew).stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);

                    User user = taskServiceFactory.getInstance(useNew).getUser(SecurityUtil.getUsername());
                    releaseNoteService.promoteTaskLineItems(branchService.getTaskBranchPathUsingCache(projectKey, taskKey), user.getDisplayName());
                    logger.info("Completed auto promotion of task {}", taskKey);
                } else {
                    ProcessStatus status = new ProcessStatus(FAILED_STATUS, merge.getApiError() == null ? "" : merge.getApiError().getMessage());
                    automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
                }
            }
        } catch (Exception e) {
            ProcessStatus status = new ProcessStatus(FAILED_STATUS, e.getMessage());
            automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
            logger.error("Failed to auto promote task {}", taskKey, e);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(null);
        }
    }

    private Merge autoPromoteTask(String projectKey, String taskKey) throws BusinessServiceException {
        ProcessStatus status = new ProcessStatus("Promoting", "");
        automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
        String taskBranchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
        return branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), null);
    }

    private Classification autoClassificationTask(String projectKey, String taskKey) throws BusinessServiceException {
        ProcessStatus status = new ProcessStatus("Classifying", "");
        automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
        String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
        try {
            Classification classification = classificationService.startClassification(projectKey, taskKey, branchPath, SecurityUtil.getUsername());
	        classification = snowstormRestClientFactory.getClient().waitForClassificationToComplete(classification);
            if (ClassificationStatus.COMPLETED == classification.getStatus()) {
                if (classification.hasEquivalentConceptsFound()) {
                    status = new ProcessStatus("Classified with equivalencies Found", "");
                    status.setCompleteDate(new Date());
                    automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
                } else if (classification.hasInferredRelationshipChangesFound()) {
                    status = new ProcessStatus("Classified with results", "");
                    status.setCompleteDate(new Date());
                    automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
                }
            } else {
                throw new BusinessServiceException(classification.getErrorMessage());
            }
            cacheService.clearClassificationCache(branchService.getProjectOrTaskBranchPathUsingCache(projectKey, taskKey));
            return classification;
        } catch (RestClientException | InterruptedException e) {
            notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, "Failed to start classification"));
            cacheService.clearClassificationCache(branchService.getProjectOrTaskBranchPathUsingCache(projectKey, taskKey));
            throw new BusinessServiceException("Failed to classify", e);
        } catch (IllegalStateException e) {
            notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, "Failed to start classification due to classification already in progress"));
            status = new ProcessStatus("Classification in progress", e.getMessage());
            automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
            cacheService.clearClassificationCache(branchService.getProjectOrTaskBranchPathUsingCache(projectKey, taskKey));
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private String autoRebaseTask(String projectKey, String taskKey, boolean useNew) throws BusinessServiceException {
        ProcessStatus status = new ProcessStatus(REBASING_STATUS, "");
        automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
        String taskBranchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);

        // Get current task and check branch state
        AuthoringTask authoringTask = taskServiceFactory.getInstance(useNew).retrieveTask(projectKey, taskKey, true, true);
        String branchState = authoringTask.getBranchState();

        // Will skip rebase process if the branch state is FORWARD or UP_TO_DATE
        if ((branchState.equalsIgnoreCase(BranchState.FORWARD.toString()) || branchState.equalsIgnoreCase(BranchState.UP_TO_DATE.toString()))) {
            return null;
        }
        try {
            String mergeId = branchService.generateBranchMergeReviews(PathHelper.getParentPath(taskBranchPath), taskBranchPath);
            SnowstormRestClient client = snowstormRestClientFactory.getClient();
            Set mergeReviewResult = client.getMergeReviewsDetails(mergeId);

            // Check conflict of merge review
            if (mergeReviewResult.isEmpty()) {

                // Process rebase task
                Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(taskBranchPath), taskBranchPath, mergeId);
                if (merge.getStatus() == Merge.Status.COMPLETED) {
                    return merge.getStatus().name();
                } else {
                    ApiError apiError = merge.getApiError();
                    String message = apiError != null ? apiError.getMessage() : null;
                    notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, message));
                    status = new ProcessStatus(REBASED_WITH_CONFLICT_STATUS, message);
                    status.setCompleteDate(new Date());
                    automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
                    return STOPPED_STATUS;
                }
            } else {
                notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, "Rebase has conflicts"));
                status = new ProcessStatus(REBASED_WITH_CONFLICT_STATUS, "");
                status.setCompleteDate(new Date());
                automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
                return STOPPED_STATUS;
            }
        } catch (InterruptedException|RestClientException e) {
            status = new ProcessStatus(FAILED_STATUS, e.getMessage());
            status.setCompleteDate(new Date());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
            throw new BusinessServiceException("Failed to start merge reviews.", e);
        }
    }

    private String parseKey(String projectKey, String taskKey) {
        return projectKey + "|" + taskKey;
    }

    private JSONObject constructCRSRequestBody(String conceptId, String branchPath, CodeSystem codeSystem, boolean includeDependencies) throws BusinessServiceException {
        ConceptPojo concept;
        String fsn;
        String dependencies = "";
        String note = "";
        try {
            SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
            concept = snowstormRestClient.getConcept(branchPath, conceptId);
            fsn = getFsn(concept.getDescriptions());

            if (includeDependencies) {
                dependencies = getDependenciesAsString(snowstormRestClient, branchPath, concept);
                note = StringUtils.hasLength(dependencies) ? "Dependencies: " + dependencies : "";
            }
        } catch (RestClientException e) {
            throw new BusinessServiceException(String.format("Concept with id %s not found against branch %s", conceptId, branchPath));
        }

        String summary = "Content promotion of " + conceptId + " |" + fsn + "|";
        if (includeDependencies && StringUtils.hasLength(dependencies)) {
            summary += " and dependency: " + dependencies;
        }
        JSONObject request = new JSONObject();
        request.put("inputMode", "SIMPLE");
        request.put("requestType", "NEW_CONCEPT");
        request.put("requestorInternalId", conceptId);
        request.put("organization", codeSystem.getShortName());
        request.put("fsn", fsn);

        JSONObject additionalFields = new JSONObject();
        additionalFields.put("topic", CONTENT_PROMOTION);
        additionalFields.put("summary", summary);
        additionalFields.put("reasonForChange", CONTENT_PROMOTION);
        additionalFields.put("notes", note);
        request.put("additionalFields", additionalFields);

        JSONObject requestItem = new JSONObject();
        requestItem.put("requestType", "NEW_CONCEPT");
        requestItem.put("topic", CONTENT_PROMOTION);
        requestItem.put("notes", note);
        requestItem.put("summary", summary);
        requestItem.put("reasonForChange", CONTENT_PROMOTION);
        requestItem.put("proposedFSN", fsn);
        requestItem.put("conceptPT", getPreferredTerm(concept.getDescriptions()));
        requestItem.put("semanticTag", getSemanticTag(fsn));


        Set<RelationshipPojo> parentConcepts = getParents(concept);
        Set<JSONObject> proposedParents = new HashSet<>();
        if (!parentConcepts.isEmpty()) {
            for (RelationshipPojo parentConcept : parentConcepts) {
                JSONObject proposedParent = new JSONObject();
                proposedParent.put("conceptId", parentConcept.getTarget().getConceptId());
                proposedParent.put("fsn", parentConcept.getTarget().getFsn().getTerm());
                proposedParent.put("refType", "EXISTING");
                proposedParent.put("sourceTerminology", "SNOMEDCT");
                proposedParents.add(proposedParent);
            }
        } else {
            logger.error("Parent concept not found");
        }
        requestItem.put("proposedParents", proposedParents);

        Set<String> proposedSynonyms = getProposedSynonyms(concept.getDescriptions());
        if (!proposedSynonyms.isEmpty()) {
            requestItem.put("proposedSynonyms", proposedSynonyms);
        }
        request.put("requestItems", Collections.singleton(requestItem));

        return request;
    }

    private Set<String> getProposedSynonyms(Set<DescriptionPojo> descriptions) {
        Set<String> synonyms = new HashSet<>();
        if (descriptions != null) {
            for (DescriptionPojo description : descriptions) {
                if (DescriptionPojo.Type.SYNONYM.equals(description.getType())
		                && description.isActive()
		                && DescriptionPojo.Acceptability.ACCEPTABLE.equals(description.getAcceptabilityMap().get(US_EN_LANG_REFSET))) {
                    synonyms.add(description.getTerm());
                }
            }
        }
        return synonyms;
    }

    private String getDependenciesAsString(SnowstormRestClient snowstormRestClient, String branchPath, ConceptPojo concept) throws RestClientException {
        Set<String> attributeTargets = new HashSet<>();
        findAttributeTargetsFromAxiom(concept.getClassAxioms(), attributeTargets);
        findAttributeTargetsFromAxiom(concept.getGciAxioms(), attributeTargets);
        // get all ancestors of all attribute targets
        StringBuilder ecl = new StringBuilder();
        for (String conceptId : attributeTargets) {
            ecl.append(!ecl.isEmpty() ? " OR >> " + conceptId : ">> " + conceptId);
        }
        Set<String> ancestors = snowstormRestClient.eclQuery(branchPath, ecl.toString(), 100, true);
        Set<SimpleConceptPojo> foundConceptMinis = snowstormRestClient.getConcepts(branchPath, null, null, new ArrayList<>(ancestors), 100, true);
        if (!foundConceptMinis.isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (SimpleConceptPojo c : foundConceptMinis) {
                if (c.isActive() && c.getModuleId().equals(concept.getModuleId())) {
                    if (!result.isEmpty()) {
                        result.append(", ");
                    }
                    result.append(c.getId() + " |" + c.getFsn().getTerm() + "|");
                }
            }
            return result.toString();
        }
        return "";
    }

    private static void findAttributeTargetsFromAxiom(Set<AxiomPojo> concept, Set<String> attributeTargets) {
        for (AxiomPojo axiom : concept) {
            if (axiom.isActive()) {
                for (RelationshipPojo rel : axiom.getRelationships()) {
                    if (rel.getTarget() != null && rel.getTarget().getConceptId() != null) {
                        attributeTargets.add(rel.getTarget().getConceptId());
                    }
                }
            }
        }
    }

    private Set<RelationshipPojo> getParents(ConceptPojo concept) {
        Set<RelationshipPojo> parents = new HashSet<>();
        if (concept != null) {
            for (AxiomPojo axiom : concept.getClassAxioms()) {
                if (axiom.isActive()) {
                    for (RelationshipPojo relationship : axiom.getRelationships()) {
                        if (IS_A.equals(relationship.getType().getConceptId())) {
                            parents.add(relationship);
                        }
                    }
                }
            }
        }
        return parents;
    }

    private String getFsn(Set<DescriptionPojo> descriptions) {
        if (descriptions != null) {
            for (DescriptionPojo description : descriptions) {
                if (DescriptionPojo.Type.FSN.equals(description.getType()) && description.isActive() && DescriptionPojo.Acceptability.PREFERRED.equals(description.getAcceptabilityMap().get(US_EN_LANG_REFSET))) {
                    return description.getTerm();
                }
            }
        }
        return null;
    }

    private String getPreferredTerm(Set<DescriptionPojo> descriptions) {
        if (descriptions != null) {
            for (DescriptionPojo description : descriptions) {
                if (DescriptionPojo.Type.SYNONYM.equals(description.getType()) && description.isActive() && DescriptionPojo.Acceptability.PREFERRED.equals(description.getAcceptabilityMap().get(US_EN_LANG_REFSET))) {
                    return description.getTerm();
                }
            }
        }
        return null;
    }

    private String getSemanticTag(String term) {
        final Matcher matcher = TAG_PATTERN.matcher(term);
        if (matcher.matches()) {
            String result = matcher.group(1);
            if (result != null && (result.contains("(") || result.contains(")"))) {
                return null;
            }
            return result;
        }
        return null;
    }

    /**
     * Aggregates promotion eligibility checks for a task.
     * Mirrors authoring-ui promotionService.checkPrerequisitesForTask.
     */
    public PromotionPrerequisites getPromotionPrerequisites(String projectKey, String taskKey, String username)
            throws BusinessServiceException {
        PromotionPrerequisites prerequisites = new PromotionPrerequisites();
        List<String> blockers = new ArrayList<>();

        AuthoringTask task = taskServiceFactory.getInstanceByKey(taskKey)
                .retrieveTask(projectKey, taskKey, true, true);

        String branchState = task.getBranchState();
        prerequisites.setBranchState(branchState);
        prerequisites.setReviewStatus(task.getStatus() != null ? task.getStatus().getLabel() : null);

        if (task.getStatus() == TaskStatus.NEW) {
            blockers.add("Task status is New");
        }

        if (isDiverged(branchState)) {
            blockers.add("Task and Project Diverged: The task and project are not synchronized. Pull in changes from the project before promotion.");
            return finalizePrerequisites(prerequisites, projectKey, taskKey, username, blockers, false);
        }

        if (BranchState.UP_TO_DATE.name().equalsIgnoreCase(nullToEmpty(branchState))) {
            blockers.add("No Changes To Promote: The task is up to date with respect to the project. No changes to promote.");
            return finalizePrerequisites(prerequisites, projectKey, taskKey, username, blockers, false);
        }

        String branchPath = StringUtils.hasLength(task.getBranchPath())
                ? task.getBranchPath()
                : branchService.getTaskBranchPathUsingCache(projectKey, taskKey);

        Branch branch;
        try {
            branch = branchService.getBranchOrNull(branchPath);
        } catch (ServiceException e) {
            throw new BusinessServiceException("Failed to retrieve branch details for " + branchPath, e);
        }

        TraceabilityClient.ActivitiesPage activities = classificationPrerequisiteService.fetchActivities(branchPath);
        Classification classification = classificationPrerequisiteService.getLatestClassificationOrNull(branchPath);
        ClassificationPrerequisiteResult classificationResult = classificationPrerequisiteService.evaluate(
                branch, classification, activities, Context.PROMOTION);
        prerequisites.setClassificationCurrent(classificationResult.classificationCurrent());
        prerequisites.setClassificationStatus(classificationResult.classificationStatus());
        prerequisites.setEquivalenciesFound(classificationResult.equivalenciesFound());
        blockers.addAll(classificationResult.blockers());

        evaluateReviewStatus(task.getStatus(), blockers);

        List<String> crsBlockingConcepts = crsBlockingStateService.formatBlockingConcepts(
                crsBlockingStateService.collectBlockingConcepts(projectKey, taskKey, username));
        prerequisites.setCrsBlockingConcepts(crsBlockingConcepts);
        for (String crsBlockingConcept : crsBlockingConcepts) {
            blockers.add("Unsaved requested promotion concept ID detected: " + crsBlockingConcept);
        }

        boolean sacSignedOff = aagClient.areTaskSacSignedOff(branchPath);
        prerequisites.setSacSignedOff(sacSignedOff);
        if (!sacSignedOff) {
            blockers.add(CRITERIA_HAVE_BEEN_SIGNED_OFF_MSG);
        }

        prerequisites.setBlockers(blockers);
        // Soft warnings stay in blockers for UI messaging; promotable tracks hard gates only
        // (branch sync, equivalencies, New, running classification, SAC) — mirrors Promote Anyway.
        prerequisites.setPromotable(!hasHardBlocker(blockers));
        return prerequisites;
    }

    private PromotionPrerequisites finalizePrerequisites(PromotionPrerequisites prerequisites, String projectKey,
            String taskKey, String username, List<String> blockers, boolean classificationCurrent) {
        prerequisites.setClassificationCurrent(classificationCurrent);
        boolean sacSignedOff = false;
        try {
            String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
            sacSignedOff = aagClient.areTaskSacSignedOff(branchPath);
        } catch (BusinessServiceException e) {
            logger.warn("Failed to resolve branch path for SAC check on {}/{}: {}", projectKey, taskKey, e.getMessage());
            sacSignedOff = true;
        }
        prerequisites.setSacSignedOff(sacSignedOff);
        if (!sacSignedOff) {
            blockers.add(CRITERIA_HAVE_BEEN_SIGNED_OFF_MSG);
        }
        List<String> crsBlockingConcepts = crsBlockingStateService.formatBlockingConcepts(
                crsBlockingStateService.collectBlockingConcepts(projectKey, taskKey, username));
        prerequisites.setCrsBlockingConcepts(crsBlockingConcepts);
        prerequisites.setBlockers(blockers);
        prerequisites.setPromotable(false);
        return prerequisites;
    }

    private static void evaluateReviewStatus(TaskStatus status, List<String> blockers) {
        if (status == null) {
            return;
        }
        if (status != TaskStatus.IN_REVIEW && status != TaskStatus.REVIEW_COMPLETED) {
            blockers.add("No review completed: No review has been completed on this task, are you sure you would like to promote?");
        }
        if (status == TaskStatus.IN_REVIEW) {
            blockers.add("Task is still in review: The task review has not been marked as complete.");
        }
    }

    /**
     * Hard blockers mirror promotionService.js blocksPromotion: true and getPromoteDisabledReason.
     */
    private static boolean hasHardBlocker(List<String> blockers) {
        for (String blocker : blockers) {
            if (blocker.startsWith("Task and Project Diverged")
                    || blocker.startsWith("No Changes To Promote")
                    || blocker.startsWith("Could Not Retrieve Branch")
                    || blocker.startsWith("Equivalencies Found")
                    || blocker.equals("Task status is New")
                    || blocker.equals("Classification is currently running")
                    || blocker.equals(CRITERIA_HAVE_BEEN_SIGNED_OFF_MSG)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDiverged(String branchState) {
        String state = nullToEmpty(branchState).toUpperCase(Locale.ROOT);
        return BranchState.BEHIND.name().equals(state)
                || BranchState.DIVERGED.name().equals(state)
                || BranchState.STALE.name().equals(state);
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public ProcessStatus getTaskPromotionStatus(String projectKey, String taskKey) {
        return taskPromotionStatus.get(parseKey(projectKey, taskKey));
    }

    public ProcessStatus getProjectPromotionStatus(String projectKey) {
        return projectPromotionStatus.get(projectKey);
    }

    public ProcessStatus getAutomateTaskPromotionStatus(String projectKey, String taskKey) {
        return automateTaskPromotionStatus.get(parseKey(projectKey, taskKey));
    }

    public void clearAutomateTaskPromotionStatus(String projectKey, String taskKey) {
        if (!automateTaskPromotionStatus.containsKey(parseKey(projectKey, taskKey))) {
            throw new ResourceNotFoundException(String.format("Automated promotion status not found for task %s", taskKey));
        }
        automateTaskPromotionStatus.remove(parseKey(projectKey, taskKey));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    public List<String> listTasksQueuedForAutoPromotion() {
        return autoPromoteBlockingQueue.stream()
                .map(AutomatePromoteProcess::getTaskKey)
                .toList();
    }
}
