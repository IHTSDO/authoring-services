package org.ihtsdo.authoringservices.service.impl;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.service.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.util.TimerUtil;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public abstract class TaskServiceBase {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String READY_FOR_REVIEW = "Ready For Review";
    public static final String CRS_JIRA_LABEL = "CRS";

    protected static final String TASK_NOT_FOUND_MSG = "Task not found ";

    private static final String INT_TASK_STATE_CHANGE_QUEUE = "int-authoring";

    @Value("${crs.int.jira.issueKey}")
    protected String intCrsIssueKeyPrefix;

    @Value("${branch.prefix.us}")
    private List<String> usBranchPrefixes;

    private static final String SHARED = "SHARED";

    @Autowired
    private UiStateService uiService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private SnowstormClassificationClient classificationService;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    protected abstract List<AuthoringTask> buildAuthoringTasks(Collection<?> tasks, List<CodeSystem> codeSystems, Boolean lightweight) throws BusinessServiceException;

    protected String toString(String projectKey, String taskKey) {
        return projectKey + "/" + taskKey;
    }

    protected void setCrsConceptsIfAny(String projectKey, String taskKey, Map<String, String> properties) {
        try {
            String conceptsStr = uiService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(projectKey, taskKey, SHARED, "crs-concepts");
            if (StringUtils.isNotEmpty(conceptsStr)) {
                properties.put("concepts", conceptsStr);
            }
        } catch (IOException e) {
            logger.error("Error while reading crs-concepts.json for task {}. Message: {}", taskKey, e.getMessage());
        }
    }

    protected boolean isIntTaskStateChangeQueue(String queue) {
        return queue.contains(INT_TASK_STATE_CHANGE_QUEUE);
    }

    protected boolean isIntAuthoringTask(String projectKey, String taskKey) throws BusinessServiceException {
        String taskBranch = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
        return usBranchPrefixes.stream().noneMatch(taskBranch::startsWith);
    }

    protected void transferTaskToNewAuthor(String projectKey, String taskKey, TaskChangeAssigneeRequest taskChangeAssigneeRequest) {
        try {
            uiService.transferTask(projectKey, taskKey, taskChangeAssigneeRequest);
            String taskReassignMessage = "Your task %s has been assigned to %s";
            String taskTakenMessage = "The task %s has been assigned to you by %s";
            if (!SecurityUtil.getUsername().equalsIgnoreCase(taskChangeAssigneeRequest.getCurrentAssignee().getUsername()) &&
                    !SecurityUtil.getUsername().equalsIgnoreCase(taskChangeAssigneeRequest.getNewAssignee().getUsername())) {
                String message = String.format(taskReassignMessage, taskKey, taskChangeAssigneeRequest.getNewAssignee().getDisplayName());
                notificationService.queueNotification(taskChangeAssigneeRequest.getCurrentAssignee().getUsername(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
                message = String.format(taskTakenMessage, taskKey, taskChangeAssigneeRequest.getCurrentLoggedUser().getDisplayName());
                notificationService.queueNotification(taskChangeAssigneeRequest.getNewAssignee().getUsername(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
            } else if (taskChangeAssigneeRequest.getNewAssignee().getUsername().equalsIgnoreCase(SecurityUtil.getUsername())) {
                String message = String.format(taskReassignMessage, taskKey, taskChangeAssigneeRequest.getCurrentAssignee().getDisplayName());
                notificationService.queueNotification(taskChangeAssigneeRequest.getCurrentAssignee().getUsername(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
            } else {
                String message = String.format(taskTakenMessage, taskKey, taskChangeAssigneeRequest.getCurrentAssignee().getDisplayName());
                notificationService.queueNotification(taskChangeAssigneeRequest.getNewAssignee().getUsername(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
            }
        } catch (BusinessServiceException e) {
            logger.error("Unable to transfer UI State in " + taskKey + " from "
                    + taskChangeAssigneeRequest.getCurrentAssignee() + " to " + taskChangeAssigneeRequest.getNewAssignee(), e);
        }
    }

    protected void setValidationStatusForAuthoringTasks(Map<String, AuthoringTask> startedTasks, TimerUtil timer) throws ExecutionException {
        Set<String> paths = startedTasks.keySet();
        final ImmutableMap<String, Validation> validationMap = validationService.getValidations(paths);
        timer.checkpoint("Recovered " + (validationMap == null ? "null" : validationMap.size()) + " ValidationStatuses");

        if (validationMap == null || validationMap.isEmpty()) {
            String branchPath = paths.iterator().next();
            logger.warn("Failed to recover validation statuses for {} branches including '{}'.", paths.size(), branchPath);
        } else {
            for (final String path : paths) {
                Validation validation = validationMap.get(path);
                if (validation != null) {
                    if (ValidationJobStatus.COMPLETED.name().equals(validation.getStatus())
                            && validation.getContentHeadTimestamp() != null
                            && !startedTasks.get(path).getBranchHeadTimestamp().equals(validation.getContentHeadTimestamp())) {
                        startedTasks.get(path).setLatestValidationStatus(ValidationJobStatus.STALE.name());
                    } else {
                        startedTasks.get(path).setLatestValidationStatus(validation.getStatus());
                    }
                }
            }
        }
    }

    protected void setTaskStatusFromAutomatedPromotionIfAny(AuthoringTask task) {
        ProcessStatus autoPromotionStatus = promotionService.getAutomateTaskPromotionStatus(task.getProjectKey(), task.getKey());
        if (autoPromotionStatus != null) {
            switch (autoPromotionStatus.getStatus()) {
                case "Queued":
                    task.setStatus(TaskStatus.AUTO_QUEUED);
                    break;
                case "Rebasing":
                    task.setStatus(TaskStatus.AUTO_REBASING);
                    break;
                case "Classifying":
                    task.setStatus(TaskStatus.AUTO_CLASSIFYING);
                    break;
                case "Promoting":
                    task.setStatus(TaskStatus.AUTO_PROMOTING);
                    break;
                case "Rebased with conflicts",
                        "Classified with results":
                    task.setStatus(TaskStatus.AUTO_CONFLICT);
                    break;
                default:
                    break;
            }
        }
    }

    protected AuthoringMain buildAuthoringMain() throws BusinessServiceException {
        try {
            String path = PathHelper.getProjectPath(null, null);
            Collection<String> paths = Collections.singletonList(path);
            final ImmutableMap<String, Validation> validationMap = validationService.getValidations(paths);
            final String branchState = branchService.getBranchStateOrNull(PathHelper.getMainPath());
            final String latestClassificationJson = classificationService
                    .getLatestClassification(PathHelper.getMainPath());
            final Validation validation = validationMap.get(path);
            return new AuthoringMain(path, branchState, validation != null ? validation.getStatus() : null, latestClassificationJson);
        } catch (ExecutionException | RestClientException | ServiceException e) {
            throw new BusinessServiceException("Failed to retrieve Main", e);
        }
    }

    protected List<AuthoringTask> buildAuthoringTasks(Collection<?> collection, Boolean lightweight) throws BusinessServiceException {
        if (collection.isEmpty()) return Collections.emptyList();

        final SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
        List<CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();
        return buildAuthoringTasks(collection, codeSystems, lightweight);
    }
}
