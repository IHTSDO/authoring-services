package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.User;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.Classification;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClientFactory;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import us.monoid.json.JSONException;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PromotionService {

    public static final Pattern TAG_PATTERN = Pattern.compile("^.*\\((.*)\\)$");
    public static final String IS_A = "116680003";

	@Autowired
	private TaskService taskService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private ContentRequestServiceClientFactory contentRequestServiceClientFactory;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReleaseNoteService releaseNoteService;

	@Autowired
	private SnowstormClassificationClient classificationService;

	private final Map<String, ProcessStatus> automateTaskPromotionStatus;

	private final Map<String, ProcessStatus> taskPromotionStatus;

	private final Map<String, ProcessStatus> projectPromotionStatus;

	private final ExecutorService executorService;

	private final LinkedBlockingQueue<AutomatePromoteProcess> autoPromoteBlockingQueue = new LinkedBlockingQueue<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public PromotionService() {
		automateTaskPromotionStatus = new HashMap<>();
		taskPromotionStatus = new HashMap<>();
		projectPromotionStatus = new HashMap<>();
		executorService = Executors.newCachedThreadPool();
	}

	public String requestConceptPromotion(String conceptId, boolean includeDependencies, String branchPath, CodeSystem codeSystem) throws BusinessServiceException {
		ContentRequestServiceClient contentRequestServiceClient = contentRequestServiceClientFactory.getClient();
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
		}
	}

	public void queueAutomateTaskPromotion(String projectKey, String taskKey) {
		AutomatePromoteProcess automatePromoteProcess = new AutomatePromoteProcess(SecurityContextHolder.getContext().getAuthentication(), projectKey, taskKey);

		try {
			ProcessStatus processStatus = new ProcessStatus("Queued","");
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);

			autoPromoteBlockingQueue.put(automatePromoteProcess);
		} catch (InterruptedException e) {
			ProcessStatus failedStatus = new ProcessStatus("Failed", e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), failedStatus);
		}
	}

	public void doTaskPromotion(String projectKey, String taskKey, MergeRequest mergeRequest) throws BusinessServiceException {
		AuthoringProject project = taskService.retrieveProject(projectKey, true);
		if (Boolean.TRUE.equals(project.isTaskPromotionDisabled())) {
			throw new BusinessServiceException("Task promotion is disabled");
		}

		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus taskProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {

				taskProcessStatus.setStatus("Rebasing");
				taskProcessStatus.setMessage("Task is rebasing");
				taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				Merge merge = branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), mergeRequest.getSourceReviewId());
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
					notificationService.queueNotification(SecurityUtil.getUsername(),
							new Notification(projectKey, taskKey, EntityType.Promotion, "Task successfully promoted"));
					taskProcessStatus.setStatus("Promotion Complete");
					taskProcessStatus.setMessage("Task successfully promoted");
					taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);

					User user = taskService.getUser(SecurityUtil.getUsername());
					releaseNoteService.promoteTaskLineItems(taskService.getTaskBranchPathUsingCache(projectKey, taskKey), user);

					// clear Auto Promotion status if the task has been triggered the Automated Promotion
					automateTaskPromotionStatus.remove(parseKey(projectKey, taskKey));
				} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
					try {
						ObjectMapper mapper = new ObjectMapper();
						String jsonInString = mapper.writeValueAsString(merge);
						taskProcessStatus.setStatus(Merge.Status.CONFLICTS.name());
						taskProcessStatus.setMessage(jsonInString);
						taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				} else {
					ApiError apiError = merge.getApiError();
					String message = apiError != null ? apiError.getMessage() : null;
					taskProcessStatus.setStatus("Promotion Error");
					taskProcessStatus.setMessage(message);
					taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				taskProcessStatus.setStatus("Promotion Error");
				taskProcessStatus.setMessage(e.getMessage());
				taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
			}
		});
	}

	public void doProjectPromotion(String projectKey, MergeRequest mergeRequest) throws BusinessServiceException {
		AuthoringProject project = taskService.retrieveProject(projectKey, true);
		if (Boolean.TRUE.equals(project.isProjectPromotionDisabled()) || Boolean.TRUE.equals(project.isProjectLocked())) {
			throw new BusinessServiceException("Project promotion is disabled");
		}
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus projectProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {

				projectProcessStatus.setStatus("Rebasing");
				projectProcessStatus.setMessage("Project is rebasing");
				projectPromotionStatus.put(projectKey, projectProcessStatus);
				String projectBranchPath = taskService.getProjectBranchPathUsingCache(projectKey);
				Merge merge = branchService.mergeBranchSync(projectBranchPath, PathHelper.getParentPath(projectBranchPath), mergeRequest.getSourceReviewId());
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					List<Issue> promotedIssues = taskService.getTaskIssues(projectKey, TaskStatus.PROMOTED);
					taskService.stateTransition(promotedIssues, TaskStatus.COMPLETED, projectKey);
					notificationService.queueNotification(SecurityUtil.getUsername(),
							new Notification(projectKey, null, EntityType.Promotion, "Project successfully promoted"));
					projectProcessStatus.setStatus("Promotion Complete");
					projectProcessStatus.setMessage("Project successfully promoted");
					projectPromotionStatus.put(projectKey, projectProcessStatus);

					releaseNoteService.promoteProjectLineItems(projectBranchPath);
				} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
					try {
						ObjectMapper mapper = new ObjectMapper();
						String jsonInString = mapper.writeValueAsString(merge);
						projectProcessStatus.setStatus(Merge.Status.CONFLICTS.name());
						projectProcessStatus.setMessage(jsonInString);
						projectPromotionStatus.put(projectKey, projectProcessStatus);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				} else {
					ApiError apiError = merge.getApiError();
					String message = apiError != null ? apiError.getMessage() : null;
					projectProcessStatus.setStatus("Promotion Error");
					projectProcessStatus.setMessage(message);
					projectPromotionStatus.put(projectKey, projectProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				projectProcessStatus.setStatus("Promotion Error");
				projectProcessStatus.setMessage(e.getMessage());
				projectPromotionStatus.put(projectKey, projectProcessStatus);
			}
		});

	}

	private synchronized void doAutomateTaskPromotion(String projectKey, String taskKey, Authentication authentication){
		SecurityContextHolder.getContext().setAuthentication(authentication);
		try {
			AuthoringProject project = taskService.retrieveProject(projectKey, true);
			if (Boolean.TRUE.equals(project.isTaskPromotionDisabled())) {
				ProcessStatus status = new ProcessStatus("Failed", "Task promotion is disabled");
				automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				logger.error("Task promotion is disabled");
				return;
			}

			// Call rebase process
			String rebaseStatus = this.autoRebaseTask(projectKey, taskKey);
			if (null != rebaseStatus && rebaseStatus.equals("stopped")) {
				return;
			}

			// Call classification process
			Classification classification = this.autoClassificationTask(projectKey, taskKey);
			if (classification != null
					&& !classification.getResults().isInferredRelationshipChangesFound()
					&& !classification.getResults().isEquivalentConceptsFound()) {

				// Call promote process
				Merge merge = this.autoPromoteTask(projectKey, taskKey);
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Promotion, "Automated promotion completed"));
					ProcessStatus status = new ProcessStatus("Completed","");
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
					taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);

					User user = taskService.getUser(SecurityUtil.getUsername());
					releaseNoteService.promoteTaskLineItems(taskService.getTaskBranchPathUsingCache(projectKey, taskKey), user);
				} else {
					ProcessStatus status = new ProcessStatus("Failed",merge.getApiError() == null ? "" : merge.getApiError().getMessage());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				}
			}
		} catch (BusinessServiceException e) {
			ProcessStatus status = new ProcessStatus("Failed", e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
			logger.error("Failed to auto promote task " + taskKey , e);
		} finally {
			SecurityContextHolder.getContext().setAuthentication(null);
		}
	}

	private Merge autoPromoteTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Promoting","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
        return branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), null);
	}

	private Classification autoClassificationTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Classifying","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String branchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		try {
			Classification classification =  classificationService.startClassification(projectKey, taskKey, branchPath, SecurityUtil.getUsername());
			classification.setResults(snowstormRestClientFactory.getClient().waitForClassificationToComplete(classification.getResults()));
			if (ClassificationStatus.COMPLETED == classification.getResults().getStatus()) {
				if (classification.getResults().isEquivalentConceptsFound()) {
					status = new ProcessStatus("Classified with equivalencies Found","");
					status.setCompleteDate(new Date());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				} else if (classification.getResults().isInferredRelationshipChangesFound()) {
					status = new ProcessStatus("Classified with results","");
					status.setCompleteDate(new Date());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				}
			} else {
				throw new BusinessServiceException(classification.getMessage());
			}
			taskService.clearClassificationCache(taskService.getBranchPathUsingCache(projectKey, taskKey));
			return classification;
		} catch (RestClientException | JSONException | InterruptedException e) {
			notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, "Failed to start classification"));
			taskService.clearClassificationCache(taskService.getBranchPathUsingCache(projectKey, taskKey));
			throw new BusinessServiceException("Failed to classify", e);
		} catch (IllegalStateException e) {
			notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, "Failed to start classification due to classification already in progress"));
			status = new ProcessStatus("Classification in progress",e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
			taskService.clearClassificationCache(taskService.getBranchPathUsingCache(projectKey, taskKey));
			return null;
		}
	}

	@SuppressWarnings("rawtypes")
	private String autoRebaseTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Rebasing","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);

		// Get current task and check branch state
		AuthoringTask authoringTask = taskService.retrieveTask(projectKey, taskKey, true);
		String branchState = authoringTask.getBranchState();

		// Will skip rebase process if the branch state is FORWARD or UP_TO_DATE
		if ((branchState.equalsIgnoreCase(BranchState.FORWARD.toString()) || branchState.equalsIgnoreCase(BranchState.UP_TO_DATE.toString())) ) {
			return null;
		} else {
			try {
				String mergeId = branchService.generateBranchMergeReviews(PathHelper.getParentPath(taskBranchPath), taskBranchPath);
				SnowstormRestClient client = snowstormRestClientFactory.getClient();
				Set mergeReviewResult =  client.getMergeReviewsDetails(mergeId);

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
						status = new ProcessStatus("Rebased with conflicts",message);
						status.setCompleteDate(new Date());
						automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
						return "stopped";
					}
				} else {
					notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, "Rebase has conflicts"));
					status = new ProcessStatus("Rebased with conflicts","");
					status.setCompleteDate(new Date());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
					return "stopped";
				}
			} catch (InterruptedException e) {
				status = new ProcessStatus("Rebased with conflicts",e.getMessage());
				automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				throw new BusinessServiceException("Failed to fetch merge reviews status.", e);
			} catch (RestClientException e) {
				status = new ProcessStatus("Rebased with conflicts",e.getMessage());
				status.setCompleteDate(new Date());
				automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				throw new BusinessServiceException("Failed to start merge reviews.", e);
			}
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
        additionalFields.put("topic", "Content Promotion");
        additionalFields.put("summary", summary);
        additionalFields.put("reasonForChange", "Content Promotion");
		additionalFields.put("notes", note);
        request.put("additionalFields", additionalFields);

        JSONObject requestItem = new JSONObject();
        requestItem.put("requestType", "NEW_CONCEPT");
        requestItem.put("topic", "Content Promotion");
		requestItem.put("notes", note);
        requestItem.put("summary", summary);
        requestItem.put("reasonForChange", "Content Promotion");
        requestItem.put("proposedFSN", fsn);
        requestItem.put("conceptPT", getPreferredTerm(concept.getDescriptions()));
        requestItem.put("semanticTag", getSemanticTag(fsn));


		Set<RelationshipPojo> parentConcepts =  getParents(concept);
        Set<JSONObject> proposedParents = new HashSet<>();
        if (parentConcepts.size() != 0) {
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
        if (proposedSynonyms.size() != 0) {
			requestItem.put("proposedSynonyms", proposedSynonyms);
		}
        request.put("requestItems", Collections.singleton(requestItem));

        return request;
    }

	private Set<String> getProposedSynonyms(Set<DescriptionPojo> descriptions) {
		Set<String> synonyms = new HashSet<>();
		if (descriptions != null) {
			for (DescriptionPojo description : descriptions) {
				if (DescriptionPojo.Type.SYNONYM.equals(description.getType()) && description.isActive() && DescriptionPojo.Acceptability.ACCEPTABLE.equals(description.getAcceptabilityMap().get(SnowstormRestClient.US_EN_LANG_REFSET))) {
					synonyms.add(description.getTerm());
				}
			}
		}
		return synonyms;
	}

	private String getDependenciesAsString(SnowstormRestClient snowstormRestClient, String branchPath, ConceptPojo concept) throws RestClientException {
		Set<String> attributeTargets = new HashSet<>();
		for (AxiomPojo axiom : concept.getClassAxioms()) {
			if (axiom.isActive()) {
				for (RelationshipPojo rel : axiom.getRelationships()) {
					attributeTargets.add(rel.getTarget().getConceptId());
				}
			}
		}
		for (AxiomPojo axiom : concept.getGciAxioms()) {
			if (axiom.isActive()) {
				for (RelationshipPojo rel : axiom.getRelationships()) {
					attributeTargets.add(rel.getTarget().getConceptId());
				}
			}
		}
		// get all ancestors of all attribute targets
		String ecl = "";
		for(String conceptId : attributeTargets) {
			ecl += (StringUtils.hasLength(ecl) ? " OR >> " + conceptId : ">> " + conceptId);
		}
		Set<String> ancestors = snowstormRestClient.eclQuery(branchPath, ecl, 100, true);
		Set<SimpleConceptPojo> foundConceptMinis = snowstormRestClient.getConcepts(branchPath, null, null, new ArrayList<>(ancestors), 100, true);
		if (foundConceptMinis.size() != 0) {
			StringBuilder result = new StringBuilder();
			for (SimpleConceptPojo c : foundConceptMinis) {
				if (c.isActive() && c.getModuleId().equals(concept.getModuleId())) {
					if (result.length() != 0) {
						result.append(", ");
					}
					result.append(c.getId() + " |" + c.getFsn().getTerm() + "|");
				}
			}
			return result.toString();
		}
		return "";
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
				if (DescriptionPojo.Type.FSN.equals(description.getType()) && description.isActive() && DescriptionPojo.Acceptability.PREFERRED.equals(description.getAcceptabilityMap().get(SnowstormRestClient.US_EN_LANG_REFSET))) {
					return description.getTerm();
				}
			}
		}
		return null;
	}

    private String getPreferredTerm(Set<DescriptionPojo> descriptions) {
        if (descriptions != null) {
            for (DescriptionPojo description : descriptions) {
                if (DescriptionPojo.Type.SYNONYM.equals(description.getType()) && description.isActive() && DescriptionPojo.Acceptability.PREFERRED.equals(description.getAcceptabilityMap().get(SnowstormRestClient.US_EN_LANG_REFSET))) {
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
            if(result != null && (result.contains("(") || result.contains(")"))) {
                return null;
            }
            return result;
        }
        return null;
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
}
