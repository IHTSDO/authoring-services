package org.ihtsdo.authoringservices.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import jakarta.jms.JMSException;
import jakarta.transaction.Transactional;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.*;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.repository.TaskRepository;
import org.ihtsdo.authoringservices.repository.TaskSequenceRepository;
import org.ihtsdo.authoringservices.service.*;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClientFactory;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.util.TimerUtil;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BadRequestException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Transactional
public class AuthoringTaskServiceImpl extends TaskServiceBase implements TaskService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final TaskStatus[] EXCLUDE_STATUSES = new TaskStatus[]{TaskStatus.COMPLETED, TaskStatus.DELETED};

    @Value("${task-state-change.notification-queues}")
    private Set<String> taskStateChangeNotificationQueues;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskSequenceRepository taskSequenceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private MessagingHelper messagingHelper;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Autowired
    private SnowstormClassificationClient classificationService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private IMSClientFactory imsClientFactory;

    @Autowired
    private ContentRequestServiceClientFactory contentRequestServiceClientFactory;

    @Autowired
    private TaskService jiraTaskService;

    @Override
    public boolean isUseNew(String taskKey) {
        Optional<Task> taskOptional = taskRepository.findById(taskKey);
        return taskOptional.isPresent();
    }

    @Override
    public AuthoringMain retrieveMain() throws BusinessServiceException {
        return buildAuthoringMain();
    }

    @Override
    public AuthoringTask createTask(String projectKey, String username, AuthoringTaskCreateRequest taskCreateRequest) throws BusinessServiceException {
        permissionService.checkUserPermissionOnProjectOrThrow(projectKey);

        Project project = getProjectOrThrow(projectKey);
        if (Boolean.FALSE.equals(project.getActive())) {
            throw new BusinessServiceException("Unable to create task on an inactive project");
        }
        Task task = new Task();
        task.setCreatedDate(Timestamp.from(Instant.now()));
        task.setUpdatedDate(Timestamp.from(Instant.now()));
        task.setName(taskCreateRequest.getSummary());
        task.setDescription(taskCreateRequest.getDescription());
        task.setReporter(username);
        task.setAssignee(taskCreateRequest.getAssignee() != null ? taskCreateRequest.getAssignee().getUsername() : username);

        if (taskCreateRequest.getCrsTasks() != null) {
            List<CrsTask> crsTasks = new ArrayList<>();
            for (CrsTask crsRequest : taskCreateRequest.getCrsTasks()) {
                CrsTask crsTask = new CrsTask();
                crsTask.setTask(task);
                crsTask.setCrsTaskKey(crsRequest.getCrsTaskKey());
                crsTask.setCrsJiraKey(crsRequest.getCrsJiraKey());
                crsTasks.add(crsTask);
            }
            task.setCrsTasks(crsTasks);
        }

        TaskSequence taskSequence = taskSequenceRepository.findOneByProject(project);
        int sequence;
        if (taskSequence != null) {
            sequence = taskSequence.getSequence() + 1;
            taskSequence.setSequence(sequence);
        } else {
            sequence = 1;
            taskSequence = new TaskSequence(project, sequence);
        }
        task.setKey(projectKey + "-" + sequence);
        task.setStatus(TaskStatus.NEW);
        task.setProject(project);
        task.setBranchPath(project.getBranchPath() + "/" + (projectKey + "-" + sequence));
        task = taskRepository.save(task);
        taskSequenceRepository.save(taskSequence);
        return buildAuthoringTasks(new ArrayList<>(List.of(task)), true).get(0);
    }

    @Override
    public AuthoringTask retrieveTask(String projectKey, String taskKey, Boolean lightweight) throws BusinessServiceException {
        Optional<Task> taskOptional = taskRepository.findById(taskKey);
        if (taskOptional.isPresent()) {
            return buildAuthoringTasks(new ArrayList<>(List.of(taskOptional.get())), lightweight).get(0);
        }
        throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + toString(projectKey, taskKey));
    }

    @Override
    public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest) throws BusinessServiceException {
        permissionService.checkUserPermissionOnProjectOrThrow(projectKey);

        Optional<Task> taskOptional = taskRepository.findById(taskKey);
        if (taskOptional.isEmpty()) {
            throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + toString(projectKey, taskKey));
        }
        Task task = taskOptional.get();

        // Act on each field received
        final TaskStatus status = taskUpdateRequest.getStatus();
        boolean isTaskStatusChanged = updateTaskStatus(status, task);

        final User assignee = taskUpdateRequest.getAssignee();
        TaskChangeAssigneeRequest taskChangeAssigneeRequest = updateTaskAssignee(assignee, task);

        final List<User> reviewers = taskUpdateRequest.getReviewers();
        List<User> newReviewersToSendEmail = new ArrayList<>();
        if (reviewers != null) {
            // Find new reviewers to send email
            newReviewersToSendEmail = getNewReviewersToSendEmail(task.getReviewers(), reviewers);
            task.setReviewers(getTaskReviewers(task, reviewers.stream().map(User::getUsername).toList()));
        }

        final String summary = taskUpdateRequest.getSummary();
        if (summary != null) {
            task.setName(summary);
        }

        final String description = taskUpdateRequest.getDescription();
        if (description != null) {
            task.setDescription(description);
        }

        task.setUpdatedDate(Timestamp.from(Instant.now()));
        task = taskRepository.save(task);

        if (isTaskStatusChanged) {
            if (!taskStateChangeNotificationQueues.isEmpty()) {
                // Send JMS Task State Notification
                sendJMSTaskStateChangeNotification(task, status);
            }

            // Send email to Author once the task has been reviewed completely
            if (TaskStatus.REVIEW_COMPLETED.equals(status)) {
                User recipient = getUser(task.getAssignee());
                emailService.sendTaskReviewCompletedNotification(projectKey, task.getKey(), task.getName(), Collections.singleton(recipient));
            }
        }
        if (taskChangeAssigneeRequest != null) {
            transferTaskToNewAuthor(projectKey, taskKey, taskChangeAssigneeRequest);
        }

        if (!newReviewersToSendEmail.isEmpty()) {
            emailService.sendTaskReviewAssignedNotification(projectKey, taskKey, task.getName(), newReviewersToSendEmail);
        }

        return buildAuthoringTasks(new ArrayList<>(List.of(task)), false).get(0);
    }

    @Nullable
    private TaskChangeAssigneeRequest updateTaskAssignee(User assignee, Task task) {
        TaskChangeAssigneeRequest taskChangeAssigneeRequest = null;
        if (assignee != null) {
            final String newAssignee = assignee.getUsername();
            String currentAssignee = task.getAssignee();
            if (currentAssignee != null && !currentAssignee.isEmpty() && !currentAssignee.equalsIgnoreCase(newAssignee)) {
                taskChangeAssigneeRequest = new TaskChangeAssigneeRequest(getUser(currentAssignee), getUser(newAssignee), getUser(SecurityUtil.getUsername()));
            }
            task.setAssignee(assignee.getUsername());
        }
        return taskChangeAssigneeRequest;
    }

    private boolean updateTaskStatus(TaskStatus status, Task task) throws BadRequestException {
        boolean isTaskStatusChanged = false;
        if (status != null) {
            TaskStatus currentStatus = task.getStatus();
            // Don't attempt to transition to the same status
            if (!status.equals(currentStatus)) {
                if (status == TaskStatus.UNKNOWN) {
                    throw new BadRequestException("Requested status is unknown.");
                }
                task.setStatus(status);
                isTaskStatusChanged = true;
            }
        }
        return isTaskStatusChanged;
    }

    private List<User> getNewReviewersToSendEmail(List<TaskReviewer> currentReviewers, List<User> reviewers) {
        List<String> currentReviewerUsername = currentReviewers != null ? currentReviewers.stream().map(TaskReviewer::getUsername).toList() : new ArrayList<>();
        List<User> results = reviewers.stream().filter(r -> !currentReviewerUsername.contains(r.getUsername()) && !SecurityUtil.getUsername().equals(r.getUsername())).toList();
        for (User user : results) {
            if (user.getEmail() == null || user.getDisplayName() == null) {
                User jiraUser = getUser(user.getUsername());
                user.setEmail(jiraUser.getEmail());
                user.setDisplayName(jiraUser.getDisplayName());
            }
        }
        return results;
    }

    @Override
    public void deleteTask(String taskKey) {
        Optional<Task> taskOptional = taskRepository.findById(taskKey);
        if (taskOptional.isEmpty()) {
            throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG);
        }
        taskRepository.delete(taskOptional.get());
    }

    @Override
    public void deleteTasks(Set<String> taskKeys) {
        taskRepository.deleteAllById(taskKeys);
    }

    @Override
    public List<AuthoringTask> getTasksByStatus(String projectKey, TaskStatus taskStatus) throws BusinessServiceException {
        List<Task> tasks = taskRepository.findByProjectAndStatus(getProjectOrThrow(projectKey), taskStatus);
        return buildAuthoringTasks(tasks, false);
    }

    @Override
    public List<AuthoringTask> listTasksForProject(String projectKey, Boolean lightweight) throws BusinessServiceException {
        List<Task> tasks = taskRepository.findByProjectAndStatusNotInOrderByUpdatedDateDesc(getProjectOrThrow(projectKey), Arrays.asList(EXCLUDE_STATUSES));
        return buildAuthoringTasks(tasks, lightweight);
    }

    @Override
    public List<AuthoringTask> listMyTasks(String username, String excludePromoted) throws BusinessServiceException {
        List<TaskStatus> excludedStatuses = new ArrayList<>(List.of(EXCLUDE_STATUSES));
        if (null != excludePromoted && excludePromoted.equalsIgnoreCase("TRUE")) {
            excludedStatuses.add(TaskStatus.PROMOTED);
        }
        List<Project> projects = permissionService.getProjectsForUser();
        List<Task> tasks = taskRepository.findByProjectInAndAssigneeAndStatusNotInOrderByUpdatedDateDesc(projects, username, excludedStatuses);
        return buildAuthoringTasks(tasks, false);
    }

    @Override
    public List<AuthoringTask> listMyOrUnassignedReviewTasks(String excludePromoted) throws BusinessServiceException {
        String currentUser = SecurityUtil.getUsername();
        List<TaskStatus> statuses = new ArrayList<>(List.of(TaskStatus.IN_REVIEW, TaskStatus.REVIEW_COMPLETED));
        if (null == excludePromoted || !excludePromoted.equalsIgnoreCase("TRUE")) {
            statuses.add(TaskStatus.PROMOTED);
        }
        List<Project> projects = permissionService.getProjectsForUser();
        List<Task> tasks = taskRepository.findByProjectInAndAssigneeNotAndStatusInOrderByUpdatedDateDesc(projects, currentUser, statuses);
        tasks = tasks.stream().
                filter(task -> (CollectionUtils.isEmpty(task.getReviewers()) && TaskStatus.IN_REVIEW.equals(task.getStatus()))
                        || task.getReviewers().stream().anyMatch(reviewer -> reviewer.getUsername().equals(currentUser))).toList();
        return buildAuthoringTasks(tasks, false);
    }

    @Override
    public List<AuthoringTask> searchTasks(String criteria, Boolean lightweight) throws BusinessServiceException {
        if (StringUtils.isEmpty(criteria)) {
            return Collections.emptyList();
        }
        String[] arrayStr = criteria.split("-");
        boolean isTaskKey = arrayStr.length == 2 && NumberUtils.isNumber(arrayStr[1]);
        if (isTaskKey) {
            Optional<Task> taskOptional = taskRepository.findById(criteria);
            if (taskOptional.isEmpty() || taskOptional.get().getStatus().equals(TaskStatus.DELETED)) {
                return Collections.emptyList();
            }
            return buildAuthoringTasks(new ArrayList<>(List.of(taskOptional.get())), lightweight);
        } else {
            List<Task> tasks = taskRepository.findByNameContaining(criteria);
            tasks = tasks.stream().filter(task -> !TaskStatus.DELETED.equals(task.getStatus())).toList();
            return buildAuthoringTasks(tasks, lightweight);
        }
    }

    @Override
    public void addCommentLogErrors(String projectKey, String taskKey, String commentString) {
        // Do nothing
    }

    @Override
    public User getUser(String username) {
        return imsClientFactory.getClient().getUserDetails(username);
    }

    @Override
    public void conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState, TaskStatus newState) {
        Optional<Task> taskOptional = taskRepository.findById(taskKey);
        if (taskOptional.isEmpty()) {
            throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + toString(projectKey, taskKey));
        }
        Task task = taskOptional.get();
        if (task.getStatus().equals(requiredState)) {
            stateTransition(newState, taskKey, projectKey);
        }
    }

    @Override
    public void stateTransition(String projectKey, String taskKey, TaskStatus newState) throws BusinessServiceException {
        try {
            this.stateTransition(newState, taskKey, projectKey);
        } catch (Exception e) {
            throw new BusinessServiceException("Failed to transition state of task " + taskKey, e);
        }
    }

    @Override
    public void stateTransition(List<AuthoringTask> tasks, TaskStatus newState, String projectKey) throws BusinessServiceException {
        for (AuthoringTask task : tasks) {
            try {
                this.stateTransition(newState, task.getKey(), projectKey);
            } catch (Exception e) {
                logger.error("Failed to transition issue {} to {}", task.getKey(), newState.getLabel());
                throw new BusinessServiceException(String.format("Failed to transition issue %s to %s", task.getKey(), newState.getLabel()), e);
            }
        }
    }

    private void stateTransition(TaskStatus newState, String taskKey, String projectKey) {
        permissionService.checkUserPermissionOnProjectOrThrow(projectKey);
        Optional<Task> taskOptional = taskRepository.findById(taskKey);
        if (taskOptional.isPresent()) {
            Task task = taskOptional.get();
            task.setStatus(newState);
            task.setUpdatedDate(Timestamp.from(Instant.now()));
            logger.info("Transition task {} to {}", taskKey, newState.getLabel());
            taskRepository.save(task);
            if (!taskStateChangeNotificationQueues.isEmpty()) {
                // Send JMS Task State Notification
                sendJMSTaskStateChangeNotification(task, newState);
            }
        }
    }

    @Override
    public List<TaskAttachment> getTaskAttachments(String projectKey, String taskKey) throws BusinessServiceException {
        List<TaskAttachment> attachments = new ArrayList<>();
        final Optional<Task> taskOptional = taskRepository.findById(taskKey);
        if (taskOptional.isEmpty()) {
            throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + toString(projectKey, taskKey));
        }

        Task task = taskOptional.get();
        for (CrsTask crsTask : task.getCrsTasks()) {
            jiraTaskService.getCrsTaskAttachment(crsTask.getCrsJiraKey(), attachments, taskKey);
        }

        return attachments;
    }

    @Override
    public void getCrsTaskAttachment(String issueLinkKey, List<TaskAttachment> attachments, String issueKey) {
        // Do nothing
    }

    @Override
    public void leaveCommentForTask(String projectKey, String taskKey, String comment) {
        // Do nothing
    }

    @Override
    public void deleteIssueLink(String issueKey, String linkId) throws BusinessServiceException {
        Optional<Task> taskOptional = taskRepository.findById(issueKey);
        if (taskOptional.isEmpty()) {
            throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + issueKey);
        }
        Task task = taskOptional.get();
        CrsTask crsTask = task.getCrsTasks().stream().filter(item -> item.getCrsJiraKey().equals(linkId)).findFirst().orElse(null);
        if (crsTask != null) {
            try {
                ContentRequestServiceClient crsClient = contentRequestServiceClientFactory.getClient();
                crsClient.unAssignAuthoringTask(crsTask.getCrsTaskKey());
                task.getCrsTasks().remove(crsTask);
                taskRepository.save(task);
            } catch (Exception e) {
                String errorMessage = String.format("Failed to un-assign the authoring task from CRS request. Error message: %s", e.getMessage());
                throw new BusinessServiceException(errorMessage, e);
            }
        }
    }

    @Override
    protected List<AuthoringTask> buildAuthoringTasks(Collection<?> collection, Boolean lightweight) throws BusinessServiceException {
        final TimerUtil timer = new TimerUtil("BuildTaskList", Level.DEBUG);
        if (collection.isEmpty()) return Collections.emptyList();

        List<Task> tasks = (List<Task>) collection;
        List<AuthoringTask> allTasks = new ArrayList<>();
        final SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
        List<CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();
        try {
            // Map of task paths to tasks
            Map<String, AuthoringTask> startedTasks = new HashMap<>();

            for (Task task : tasks) {
                buildAuthoringTask(lightweight, task, allTasks, codeSystems, timer, startedTasks);
            }

            if (!Boolean.TRUE.equals(lightweight) && (!startedTasks.isEmpty())) {
                setValidationStatusForAuthoringTasks(startedTasks, timer);
            }

            timer.finish();
        } catch (ExecutionException | RestClientException | ServiceException e) {
            throw new BusinessServiceException("Failed to retrieve task list.", e);
        }
        return allTasks;
    }

    private void buildAuthoringTask(Boolean lightweight, Task task, List<AuthoringTask> allTasks, List<CodeSystem> codeSystems, TimerUtil timer, Map<String, AuthoringTask> startedTasks) throws ServiceException, RestClientException {
        AuthoringTask authoringTask = new AuthoringTask(task);
        authoringTask.setInternalAuthoringTask(true);

        if (!CollectionUtils.isEmpty(task.getCrsTasks())) {
            authoringTask.setLabels(new Gson().toJson(List.of(CRS_JIRA_LABEL)));
        }

        // assignee, reporter, reviewers
        joinTaskUsers(task, authoringTask);

        setTaskStatusFromAutomatedPromotionIfAny(authoringTask);

        allTasks.add(authoringTask);

        // Fetch latest code system version timestamp
        if (lightweight == null || !lightweight) {
            setLatestCodeSystemVersionTimestampToAuthoringTask(authoringTask, task.getProject().getBranchPath(), codeSystems);
        }

        // Fetch the extra statuses for tasks that are not new and have a branch
        if (authoringTask.getStatus() != TaskStatus.NEW) {
            Branch branch = branchService.getBranchOrNull(authoringTask.getBranchPath());
            timer.checkpoint("Recovered branch state");
            if (branch != null) {
                authoringTask.setBranchState(branch.getState());
                authoringTask.setBranchBaseTimestamp(branch.getBaseTimestamp());
                authoringTask.setBranchHeadTimestamp(branch.getHeadTimestamp());

                if (lightweight == null || !lightweight) {
                    authoringTask.setLatestClassificationJson(classificationService.getLatestClassification(authoringTask.getBranchPath()));
                    timer.checkpoint("Recovered classification");
                    // get the review message details and append to task
                    TaskMessagesDetail detail = reviewService.getTaskMessagesDetail(authoringTask.getProjectKey(), authoringTask.getKey(), SecurityUtil.getUsername());
                    authoringTask.setFeedbackMessagesStatus(detail.getTaskMessagesStatus());
                    authoringTask.setFeedbackMessageDate(detail.getLastMessageDate());
                    authoringTask.setViewDate(detail.getViewDate());
                    timer.checkpoint("Recovered feedback messages");
                }
                startedTasks.put(authoringTask.getBranchPath(), authoringTask);
            }
        }
    }

    private void joinTaskUsers(Task task, AuthoringTask authoringTask) {
        if (org.springframework.util.StringUtils.hasLength(task.getAssignee())) {
            authoringTask.setAssignee(getUser(task.getAssignee()));
        }
        if (org.springframework.util.StringUtils.hasLength(task.getReporter())) {
            authoringTask.setReporter(getUser(task.getReporter()));
        }
        if (task.getReviewers() != null) {
            List<User> reviewers = new ArrayList<>();
            for (TaskReviewer reviewer : task.getReviewers()) {
                reviewers.add(getUser(reviewer.getUsername()));
            }
            authoringTask.setReviewers(reviewers);
        }
    }

    private void setLatestCodeSystemVersionTimestampToAuthoringTask(AuthoringTask task, String projectBranchPath, List<CodeSystem> codeSystems) {
        String projectParentPath = PathHelper.getParentPath(projectBranchPath);
        CodeSystem codeSystem = codeSystems.stream().filter(c -> projectParentPath.equals(c.getBranchPath())).findFirst().orElse(null);
        if (codeSystem == null && projectParentPath.contains("/")) {
            // Attempt match using branch grandfather
            String grandfatherPath = PathHelper.getParentPath(projectParentPath);
            codeSystem = codeSystems.stream().filter(c -> grandfatherPath.equals(c.getBranchPath())).findFirst().orElse(null);
        }
        if (codeSystem != null && codeSystem.getLatestVersion() != null) {
            task.setLatestCodeSystemVersionTimestamp(codeSystem.getLatestVersion().getImportDate().getTime());
        }
    }

    private Project getProjectOrThrow(String projectKey) throws BusinessServiceException {
        Optional<Project> projectOptional = projectRepository.findById(projectKey);
        if (projectOptional.isEmpty()) {
            throw new BusinessServiceException("Project with key " + projectKey + " not found");
        }
        return projectOptional.get();
    }

    private void sendJMSTaskStateChangeNotification(Task task, TaskStatus newState) {
        try {
            Map<String, String> properties = new HashMap<>();
            properties.put("key", task.getKey());
            properties.put("status", newState.getLabel());

            if (TaskStatus.COMPLETED.equals(newState)) {
                setCrsConceptsIfAny(task.getProject().getKey(), task.getKey(), properties);
            }

            if (task.getCrsTasks() != null) {
                properties.put("labels", CRS_JIRA_LABEL);
                properties.put("internalAuthoringTask", Boolean.TRUE.toString());
                properties.put("crsJiraKeys", task.getCrsTasks().stream().map(CrsTask::getCrsJiraKey).collect(Collectors.joining(",")));
                for (String queue : taskStateChangeNotificationQueues) {
                    if ((isIntAuthoringTask(task) && isIntTaskStateChangeQueue(queue))
                            || (!isIntAuthoringTask(task) && !isIntTaskStateChangeQueue(queue))) {
                        messagingHelper.send(new ActiveMQQueue(queue), properties);
                    }
                }
            }

        } catch (JsonProcessingException | JMSException e) {
            logger.error("Failed to send task state change notification for {} {}.", task.getKey(), newState.getLabel(), e);
        }
    }

    private boolean isIntAuthoringTask(Task task) {
        return task.getCrsTasks().stream().anyMatch(crsTask -> crsTask.getCrsJiraKey().startsWith(intCrsIssueKeyPrefix));
    }

    private List<TaskReviewer> getTaskReviewers(Task task, List<String> reviewers) {
        List<TaskReviewer> existing = Objects.requireNonNullElseGet(task.getReviewers(), ArrayList::new);
        reviewers.forEach(item -> {
            TaskReviewer found = existing.stream().filter(e -> e.getUsername().equals(item)).findFirst().orElse(null);
            if (found == null) {
                existing.add(new TaskReviewer(task, item));
            }
        });
        existing.removeIf(item -> !reviewers.contains(item.getUsername()));
        return existing;
    }
}
