package org.ihtsdo.authoringservices.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Sets;
import jakarta.annotation.PostConstruct;
import jakarta.jms.JMSException;
import net.rcarz.jiraclient.Status;
import net.rcarz.jiraclient.*;
import net.sf.json.JSON;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.Task;
import org.ihtsdo.authoringservices.entity.TaskReviewer;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.repository.TaskRepository;
import org.ihtsdo.authoringservices.service.*;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClientFactory;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.ihtsdo.authoringservices.service.impl.JiraProjectServiceImpl.LIMIT_UNLIMITED;
import static org.ihtsdo.authoringservices.service.impl.JiraProjectServiceImpl.UNIT_TEST;

public class JiraTaskServiceImpl extends TaskServiceBase implements TaskService {

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public static final String INCLUDE_ALL_FIELDS = "*all";
    private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";
    private static final String EXCLUDE_STATUSES = " AND (status != \"" + TaskStatus.COMPLETED.getLabel()
            + "\" AND status != \"" + TaskStatus.DELETED.getLabel() + "\") ";
    private static final String OR_STATUS_CLAUSE = "\" OR status = \"";

    @Autowired
    private BranchService branchService;

    @Autowired
    private SnowstormClassificationClient classificationService;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private InstanceConfiguration instanceConfiguration;

    @Autowired
    private MessagingHelper messagingHelper;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ProjectService jiraProjectService;

    @Value("${task-state-change.notification-queues}")
    private Set<String> taskStateChangeNotificationQueues;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ContentRequestServiceClientFactory contentRequestServiceClientFactory;

    @Autowired
    private UiConfiguration uiConfiguration;

    private final ImpersonatingJiraClientFactory jiraClientFactory;
    private final Set<String> myTasksRequiredFields;
    private final String jiraCrsIdField;
    private final String jiraOrganizationField;
    private final String jiraReviewerField;
    private final String jiraReviewersField;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public JiraTaskServiceImpl(ImpersonatingJiraClientFactory jiraClientFactory, String jiraUsername) throws JiraException {
        this.jiraClientFactory = jiraClientFactory;
        if (!jiraUsername.equals(UNIT_TEST)) {
            final JiraClient jiraClientForFieldLookup = jiraClientFactory.getAdminInstance();

            jiraReviewerField = JiraHelper.fieldIdLookup("Reviewer", jiraClientForFieldLookup, null);
            jiraReviewersField = JiraHelper.fieldIdLookup("Reviewers", jiraClientForFieldLookup, null);
            jiraOrganizationField = JiraHelper.fieldIdLookup("Organization", jiraClientForFieldLookup, null);
            jiraCrsIdField = JiraHelper.fieldIdLookup("CRS-ID", jiraClientForFieldLookup, null);
        } else {
            jiraReviewerField = null;
            jiraReviewersField = null;
            jiraOrganizationField = null;
            jiraCrsIdField = null;
        }

        myTasksRequiredFields = Sets.newHashSet(
                Field.PROJECT,
                Field.SUMMARY,
                Field.STATUS,
                Field.DESCRIPTION,
                Field.ASSIGNEE,
                Field.CREATED_DATE,
                Field.UPDATED_DATE,
                Field.LABELS,
                jiraReviewerField,
                jiraReviewersField);
    }

    @PostConstruct
    public void postConstruct() {
        logger.info("{} task state notification queues configured {}", taskStateChangeNotificationQueues.size(), taskStateChangeNotificationQueues);
    }

    @Override
    public void deleteTask(String taskKey) throws BusinessServiceException {
        logger.info("Deleting JIRA issue {}", taskKey);
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        try {
            JiraHelper.deleteIssue(adminJiraClient, taskKey);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to delete JIRA issue " + taskKey, e);
        }
    }

    @Override
    public void deleteTasks(Set<String> taskKeys) throws BusinessServiceException {
        logger.info("Deleting JIRA issues [{}]", taskKeys);
        for (String issueKey : taskKeys) {
            deleteTask(issueKey);
        }
    }

    @Override
    public boolean isUseNew(String taskKey) {
        return false;
    }

    @Override
    public AuthoringMain retrieveMain() throws BusinessServiceException {
        return buildAuthoringMain();
    }

    @Override
    public List<AuthoringTask> getTasksByStatus(String projectKey, TaskStatus taskStatus) throws BusinessServiceException {
        try {
            List<Issue> issues = getJiraClient().searchIssues(getProjectTaskJQL(projectKey, taskStatus), -1).issues;
            return buildAuthoringTasks(issues, false);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to load tasks in project '" + projectKey + "' from Jira.", e);
        }
    }

    @Override
    public List<AuthoringTask> listTasksForProject(String projectKey, Boolean lightweight) throws BusinessServiceException {
        getProjectOrThrow(projectKey);
        List<Issue> issues;
        try {
            issues = searchIssues(getProjectTaskJQL(projectKey, null), LIMIT_UNLIMITED);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to list tasks.", e);
        }
        return buildAuthoringTasks(issues, lightweight);
    }

    @Override
    public AuthoringTask retrieveTask(String projectKey, String taskKey, Boolean lightweight, boolean skipTaskMigration) throws BusinessServiceException {
        try {
            Issue issue = getIssue(taskKey);
            final List<AuthoringTask> authoringTasks = buildAuthoringTasks(new ArrayList<>(List.of(issue)), lightweight);
            AuthoringTask authoringTask = !authoringTasks.isEmpty() ? authoringTasks.get(0) : null;

            // Migrate JIRA task to internal authoring project
            if (!skipTaskMigration && authoringTask != null && !TaskStatus.DELETED.equals(authoringTask.getStatus())) {
                Optional<Task> existingTaskOptional = taskRepository.findById(issue.getKey());
                Optional<Project> projectOptional = projectRepository.findById(projectKey);
                if (projectOptional.isPresent() && existingTaskOptional.isEmpty()) {
                    migrateJiraTaskToInternalProject(authoringTask, projectOptional.get());
                    authoringTask.setInternalAuthoringTask(true);
                }
            }

            return authoringTask;
        } catch (JiraException e) {
            if (e.getCause() instanceof RestException restException && restException.getHttpStatusCode() == 404) {
                throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + toString(projectKey, taskKey), e);
            }
            throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
        }
    }

    private void migrateJiraTaskToInternalProject(AuthoringTask authoringTask, Project project) throws BusinessServiceException {
        if (authoringTask.getLabels() != null && authoringTask.getLabels().contains(CRS_JIRA_LABEL)) return;

        Task task = new Task();
        task.setKey(authoringTask.getKey());
        task.setProject(project);
        task.setName(authoringTask.getSummary());
        task.setAssignee(authoringTask.getAssignee() != null ? authoringTask.getAssignee().getUsername() : null);
        task.setReporter(authoringTask.getReporter().getUsername());
        task.setDescription(authoringTask.getDescription());
        task.setStatus(authoringTask.getStatus());
        task.setBranchPath(project.getBranchPath() + "/" + authoringTask.getKey());
        if (authoringTask.getReviewers() != null) {
            List<TaskReviewer> reviewers = new ArrayList<>();
            authoringTask.getReviewers().forEach(item ->
                reviewers.add(new TaskReviewer(task, item.getUsername()))
            );
            task.setReviewers(reviewers);
        }
        task.setCreatedDate(getTimestamp(authoringTask.getCreated()));
        task.setUpdatedDate(getTimestamp(authoringTask.getUpdated()));
        taskRepository.save(task);
    }

    private Timestamp getTimestamp(String date) throws BusinessServiceException {
        try {
            return Timestamp.from(Instant.ofEpochMilli(formatter.parse(date).getTime()));
        } catch (ParseException e) {
            throw new BusinessServiceException("Failed to parse date", e);
        }
    }

    @Override
    public Integer getLatestTaskNumberForProject(String projectKey) {
        try {
            List<Issue> issues = searchIssues("project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\" ORDER BY created DESC", 1);
            List<Integer> taskNumbers = new ArrayList<>();
            for (Issue issue : issues) {
                String[] arr = issue.getKey().split("-");
                taskNumbers.add(Integer.parseInt(arr[arr.length - 1]));
            }
            OptionalInt maxNumber = taskNumbers.stream().mapToInt(Integer::intValue).max();
            return maxNumber.orElse(0);
        } catch (Exception e) {
            logger.warn("Failed to get the latest JIRA ticket for project {}", projectKey, e);
            return null;
        }
    }

    private Issue getIssue(String taskKey) throws JiraException {
        return getIssue(taskKey, false);
    }

    private Issue getIssue(String taskKey, boolean includeAll) throws JiraException {
        // If we don't need all fields, then the existing implementation is
        // sufficient
        if (includeAll) {
            return getJiraClient().getIssue(taskKey, INCLUDE_ALL_FIELDS, "changelog");
        } else {
            return getJiraClient().getIssue(taskKey, INCLUDE_ALL_FIELDS);
        }
    }

    /**
     * @param jql
     * @param limit maximum number of issues to return. If -1 the results are
     *              unlimited.
     * @return
     * @throws JiraException
     */
    private List<Issue> searchIssues(String jql, int limit) throws JiraException {
        return searchIssues(jql, limit, null);
    }

    private List<Issue> searchIssues(String jql, int limit, Set<String> requiredFields) throws JiraException {
        List<Issue> issues = new ArrayList<>();
        Issue.SearchResult searchResult;
        String requiredFieldParam = null;
        if (requiredFields != null && !requiredFields.isEmpty()) {
            requiredFieldParam = String.join(",", requiredFields);
        }
        do {
            searchResult = getJiraClient().searchIssues(jql, requiredFieldParam, limit - issues.size(), issues.size());
            issues.addAll(searchResult.issues);
        } while (searchResult.total > issues.size() && (limit == LIMIT_UNLIMITED || issues.size() < limit));

        return issues;

    }

    /**
     * Search issues based on Jira Summary or ID field
     */
    @Override
    public List<AuthoringTask> searchTasks(String criteria, Boolean lightweight) throws BusinessServiceException {
        if (StringUtils.isEmpty(criteria)) {
            return Collections.emptyList();
        }
        String[] arrayStr = criteria.split("-");
        boolean isJiraId = arrayStr.length == 2 && NumberUtils.isNumber(arrayStr[1]);
        String jql = "type = \"" + AUTHORING_TASK_TYPE + "\"" + " AND status != \"" + TaskStatus.DELETED.getLabel() + "\"" + (isJiraId ? " AND id = \"" + criteria + "\"" : " AND summary ~ \"" + criteria + "\"");
        List<Issue> issues;
        try {
            issues = searchIssues(jql, LIMIT_UNLIMITED, myTasksRequiredFields);
        } catch (JiraException exception) {
            if (isJiraId) {
                return Collections.emptyList();
            } else {
                throw new BusinessServiceException("Failed to search tasks", exception);
            }
        }
        return buildAuthoringTasks(issues, lightweight != null && lightweight);
    }

    @Override
    public List<AuthoringTask> listMyTasks(String username, String excludePromoted, TaskType type) throws BusinessServiceException {
        String jql = "assignee = \"" + username + "\" AND type = \"" + AUTHORING_TASK_TYPE + "\" " + EXCLUDE_STATUSES;
        if (null != excludePromoted && excludePromoted.equalsIgnoreCase("TRUE")) {
            jql += " AND status != \"Promoted\"";
        }
        List<Issue> issues;
        try {
            issues = searchIssues(jql, LIMIT_UNLIMITED, myTasksRequiredFields);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to list my tasks", e);
        }
        List<AuthoringTask> tasks = buildAuthoringTasks(issues, false);
        if (TaskType.CRS.equals(type)) {
            tasks = tasks.stream().filter(task -> task.getLabels() != null && task.getLabels().contains(CRS_JIRA_LABEL)).toList();
        }
        return tasks;
    }

    @Override
    public List<AuthoringTask> listMyOrUnassignedReviewTasks(String excludePromoted) throws BusinessServiceException {
        String jql = "type = \"" + AUTHORING_TASK_TYPE + "\" " + "AND assignee != currentUser() AND "
                + "(((Reviewer = null AND Reviewers = null) AND status = \"" + TaskStatus.IN_REVIEW.getLabel() + "\") OR ((Reviewer = currentUser() OR Reviewers = currentUser()) AND ";
        if (null != excludePromoted && excludePromoted.equalsIgnoreCase("TRUE")) {
            jql += "(status = \"" + TaskStatus.IN_REVIEW.getLabel() + OR_STATUS_CLAUSE + TaskStatus.REVIEW_COMPLETED.getLabel() + "\")))";
        } else {
            jql += "(status = \"" + TaskStatus.IN_REVIEW.getLabel() + OR_STATUS_CLAUSE + TaskStatus.REVIEW_COMPLETED.getLabel() + OR_STATUS_CLAUSE + TaskStatus.PROMOTED.getLabel() + "\")))";
        }
        List<Issue> issues;
        try {
            issues = searchIssues(jql, LIMIT_UNLIMITED);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to list my or unassigned review tasks", e);
        }
        return buildAuthoringTasks(issues, false);
    }

    private String getProjectTaskJQL(String projectKey, TaskStatus taskStatus) {
        String jql = "project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\" " + EXCLUDE_STATUSES;
        if (taskStatus != null) {
            jql += " AND status = \"" + taskStatus.getLabel() + "\"";
        }
        return jql;
    }

    @Override
    public AuthoringTask createTask(String projectKey, String username, AuthoringTaskCreateRequest taskCreateRequest, TaskType type)
            throws BusinessServiceException {
        Issue jiraIssue;
        try {
            jiraIssue = getJiraClient().createIssue(projectKey, AUTHORING_TASK_TYPE)
                    .field(Field.SUMMARY, taskCreateRequest.getSummary())
                    .field(Field.DESCRIPTION, taskCreateRequest.getDescription()).field(Field.ASSIGNEE, username)
                    .execute();
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to create Jira task", e);
        }

        AuthoringTask authoringTask = new AuthoringTask(jiraIssue, jiraProjectService.getProjectBaseUsingCache(projectKey), jiraReviewerField, jiraReviewersField);
        // Create project branch if needed
        try {
            branchService.createBranchIfNeeded(PathHelper.getParentPath(authoringTask.getBranchPath()));
        } catch (ServiceException e) {
            throw new BusinessServiceException("Failed to create project branch.", e);
        }
        // Task branch creation is delayed until the user starts work to prevent
        // having to rebase straight away.
        return authoringTask;
    }

    @Override
    protected List<AuthoringTask> buildAuthoringTasks(Collection<?> collection, Boolean lightweight) throws BusinessServiceException {
        final TimerUtil timer = new TimerUtil("BuildTaskList", Level.DEBUG);
        List<Issue> tasks = (List<Issue>) collection;
        List<AuthoringTask> allTasks = new ArrayList<>();
        final SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
        List<CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();
        try {
            // Map of task paths to tasks
            Map<String, AuthoringTask> startedTasks = new HashMap<>();

            Set<String> projectKeys = new HashSet<>();
            for (Issue issue : tasks) {
                projectKeys.add(issue.getProject().getKey());
            }

            final Map<String, ProjectDetails> projectKeyToBranchBaseMap = jiraProjectService.getProjectDetailsCache().getAll(projectKeys);
            for (Issue issue : tasks) {
                buildAuthoringTask(lightweight, issue, projectKeyToBranchBaseMap, allTasks, codeSystems, timer, startedTasks);
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


    private void buildAuthoringTask(Boolean lightweight, Issue issue, Map<String, ProjectDetails> projectKeyToBranchBaseMap, List<AuthoringTask> allTasks, List<CodeSystem> codeSystems, TimerUtil timer, Map<String, AuthoringTask> startedTasks) throws ServiceException, RestClientException {
        final ProjectDetails projectDetails = projectKeyToBranchBaseMap.get(issue.getProject().getKey());
        if (instanceConfiguration.isJiraProjectVisible(projectDetails.productCode())) {
            AuthoringTask task = new AuthoringTask(issue, projectDetails.baseBranchPath(), jiraReviewerField, jiraReviewersField);

            setTaskStatusFromAutomatedPromotionIfAny(task);

            allTasks.add(task);

            // Fetch latest code system version timestamp
            if (lightweight == null || !lightweight) {
                setLatestCodeSystemVersionTimestampToAuthoringTask(issue, codeSystems, projectDetails, task);
            }

            // Fetch the extra statuses for tasks that are not new and have a branch
            if (task.getStatus() != TaskStatus.NEW) {
                Branch branch = branchService.getBranchOrNull(task.getBranchPath());
                timer.checkpoint("Recovered branch state");
                if (branch != null) {
                    task.setBranchState(branch.getState());
                    task.setBranchBaseTimestamp(branch.getBaseTimestamp());
                    task.setBranchHeadTimestamp(branch.getHeadTimestamp());

                    if (lightweight == null || !lightweight) {
                        task.setLatestClassificationJson(classificationService.getLatestClassification(task.getBranchPath()));
                        timer.checkpoint("Recovered classification");
                        // get the review message details and append to task
                        TaskMessagesDetail detail = reviewService.getTaskMessagesDetail(task.getProjectKey(), task.getKey(), getUsername());
                        task.setFeedbackMessagesStatus(detail.getTaskMessagesStatus());
                        task.setFeedbackMessageDate(detail.getLastMessageDate());
                        task.setViewDate(detail.getViewDate());
                        timer.checkpoint("Recovered feedback messages");
                    }
                    startedTasks.put(task.getBranchPath(), task);
                }
            }
        }
    }

    private void setLatestCodeSystemVersionTimestampToAuthoringTask(Issue issue, List<CodeSystem> codeSystems, ProjectDetails projectDetails, AuthoringTask task) {
        final String projectPath = PathHelper.getProjectPath(projectDetails.baseBranchPath(), issue.getProject().getKey());
        String projectParentPath = PathHelper.getParentPath(projectPath);
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

    private void getProjectOrThrow(String projectKey) {
        try {
            getJiraClient().getProject(projectKey);
        } catch (JiraException e) {
            throw new ResourceNotFoundException("Project", projectKey);
        }
    }


    @Override
    public void addCommentLogErrors(String projectKey, String taskKey, String commentString) {
        try {
            addComment(taskKey, commentString);
        } catch (JiraException e) {
            logger.error("Failed to set message on jira ticket {}/{}: {}", projectKey, taskKey, commentString, e);
        }
    }

    private void addComment(String taskKey, String commentString) throws JiraException {
        Issue issue = getIssue(taskKey);
        issue.addComment(commentString);
        issue.update(); // Pick up new comment locally too
    }

    private JiraClient getJiraClient() {
        return jiraClientFactory.getImpersonatingInstance(getUsername());
    }

    private String getUsername() {
        return SecurityUtil.getUsername();
    }

    @Override
    public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest) throws BusinessServiceException {
        try {
            Issue issue = getIssue(taskKey);
            final List<AuthoringTask> authoringTasks = buildAuthoringTasks(new ArrayList<>(List.of(issue)), true);
            if (authoringTasks.isEmpty()) {
                throw new ResourceNotFoundException("Task", taskKey);
            }
            AuthoringTask authoringTask = authoringTasks.get(0);
            // Act on each field received
            final TaskStatus status = taskUpdateRequest.getStatus();

            if (status != null) {
                updateTaskStatus(projectKey, taskKey, issue, status, authoringTask);
            }

            final Issue.FluentUpdate updateRequest = issue.update();
            boolean fieldUpdates = false;

            final org.ihtsdo.authoringservices.domain.User assignee = taskUpdateRequest.getAssignee();
            TaskChangeAssigneeRequest taskChangeAssigneeRequest = null;
            if (assignee != null) {
                taskChangeAssigneeRequest = updateTaskAssigneeAndReturnTaskTransferRequestIfAny(assignee, updateRequest, issue, taskChangeAssigneeRequest);
                fieldUpdates = true;
            }

            final List<org.ihtsdo.authoringservices.domain.User> reviewers = taskUpdateRequest.getReviewers();
            if (reviewers != null) {
                if (!reviewers.isEmpty()) {
                    updateTaskReviewers(projectKey, taskKey, reviewers, authoringTask, updateRequest);
                    fieldUpdates = true;
                } else {
                    updateRequest.field(jiraReviewerField, null);
                    updateRequest.field(jiraReviewersField, null);
                    fieldUpdates = true;
                }
            }

            final String summary = taskUpdateRequest.getSummary();
            if (summary != null) {
                updateRequest.field(Field.SUMMARY, summary);
                fieldUpdates = true;
            }

            final String description = taskUpdateRequest.getDescription();
            if (description != null) {
                updateRequest.field(Field.DESCRIPTION, description);
                fieldUpdates = true;
            }

            if (fieldUpdates) {
                updateRequest.execute();
                // If the JIRA update goes through, then we can move any
                // UI-State over if required
                if (taskChangeAssigneeRequest != null) {
                    transferTaskToNewAuthor(projectKey, taskKey, taskChangeAssigneeRequest);
                }
            }
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to update task due to " + findRootCause(e).getMessage(), e);
        }

        // Pick up those changes in a new Task object
        return retrieveTask(projectKey, taskKey, false, true);
    }

    private TaskChangeAssigneeRequest updateTaskAssigneeAndReturnTaskTransferRequestIfAny(org.ihtsdo.authoringservices.domain.User assignee, Issue.FluentUpdate updateRequest, Issue issue, TaskChangeAssigneeRequest taskChangeAssigneeRequest) throws BusinessServiceException {
        final String username = assignee.getUsername();
        if (username == null || username.isEmpty()) {
            updateRequest.field(Field.ASSIGNEE, null);
        } else {
            updateRequest.field(Field.ASSIGNEE, getJiraUser(username));
            String currentIssueAssignee = issue.getAssignee().getName();
            if (currentIssueAssignee != null && !currentIssueAssignee.isEmpty() && !currentIssueAssignee.equalsIgnoreCase(username)) {
                taskChangeAssigneeRequest = new TaskChangeAssigneeRequest(getUser(currentIssueAssignee), getUser(username), getUser(SecurityUtil.getUsername()));
            }
        }
        return taskChangeAssigneeRequest;
    }

    private void updateTaskReviewers(String projectKey, String taskKey, List<org.ihtsdo.authoringservices.domain.User> reviewers, AuthoringTask authoringTask, Issue.FluentUpdate updateRequest) throws BusinessServiceException {
        List<net.rcarz.jiraclient.User> users = new ArrayList<>();

        for (User reviewer : reviewers) {
            users.add(getJiraUser(reviewer.getUsername()));
        }
        // Send email to new reviewers
        emailService.sendTaskReviewAssignedNotification(projectKey, taskKey, authoringTask.getSummary(), getNewReviewers(authoringTask.getReviewers(), reviewers));

        updateRequest.field(jiraReviewerField, null);
        updateRequest.field(jiraReviewersField, users);
    }

    private void updateTaskStatus(String projectKey, String taskKey, Issue issue, TaskStatus status, AuthoringTask authoringTask) throws JiraException, BusinessServiceException {
        Status currentStatus = issue.getStatus();
        // Don't attempt to transition to the same status
        if (!status.getLabel().equalsIgnoreCase(currentStatus.getName())) {
            if (status == TaskStatus.UNKNOWN) {
                throw new BadRequestException("Requested status is unknown.");
            }
            stateTransition(taskKey, status, projectKey);

            // Send email to Author once the task has been reviewed completely
            if (TaskStatus.REVIEW_COMPLETED.equals(status)) {
                emailService.sendTaskReviewCompletedNotification(projectKey, taskKey, authoringTask.getSummary(), Collections.singleton(authoringTask.getAssignee()));
            }
        }
    }

    public static Throwable findRootCause(Throwable throwable) {
        Objects.requireNonNull(throwable);
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }


    private Collection<User> getNewReviewers(List<User> currentReviewers, List<User> reviewers) throws BusinessServiceException {
        reviewers.removeAll(currentReviewers);
        Collection<org.ihtsdo.authoringservices.domain.User> results =
                reviewers.stream().filter(r -> !SecurityUtil.getUsername().equals(r.getUsername())).toList();

        for (User user : results) {
            if (user.getEmail() == null || user.getDisplayName() == null) {
                net.rcarz.jiraclient.User jiraUser = getJiraUser(user.getUsername());
                user.setEmail(jiraUser.getEmail());
                user.setDisplayName(jiraUser.getDisplayName());
            }
        }
        return results;
    }

    @Override
    public User getUser(String username) throws BusinessServiceException {
        net.rcarz.jiraclient.User result = getJiraUser(username);
        User user = new User();
        user.setUsername(result.getName());
        user.setDisplayName(result.getDisplayName());
        user.setEmail(result.getEmail());
        return user;
    }

    private net.rcarz.jiraclient.User getJiraUser(String username) throws BusinessServiceException {
        try {
            return net.rcarz.jiraclient.User.get(getJiraClient().getRestClient(), username);
        } catch (JiraException je) {
            throw new BusinessServiceException("Failed to recover user '" + username + "' from Jira instance.", je);
        }
    }

    @Override
    public void conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState,
                                           TaskStatus newState) throws BusinessServiceException {
        final Issue issue;
        try {
            issue = getIssue(taskKey);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to get issue " + taskKey, e);
        }
        if (TaskStatus.fromLabel(issue.getStatus().getName()) == requiredState) {
            try {
                stateTransition(taskKey, newState, projectKey);
            } catch (JiraException e) {
                throw new BusinessServiceException("Failed to transition state of task " + taskKey, e);
            }
        }
    }

    @Override
    public void stateTransition(String projectKey, String taskKey, TaskStatus newState)
            throws BusinessServiceException {
        try {
            stateTransition(taskKey, newState, projectKey);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to transition state of task " + taskKey, e);
        }
    }

    @Override
    public void stateTransition(List<AuthoringTask> tasks, TaskStatus newState, String projectKey) throws BusinessServiceException {
        for (AuthoringTask task : tasks) {
            try {
                stateTransition(task.getKey(), newState, projectKey);
            } catch (JiraException | BusinessServiceException e) {
                logger.error("Failed to transition issue {} to {}", task.getKey(), newState.getLabel());
                throw new BusinessServiceException(String.format("Failed to transition issue %s to %s", task.getKey(), newState.getLabel()), e);
            }
        }
    }

    private void stateTransition(String taskKey, TaskStatus newState, String projectKey) throws JiraException, BusinessServiceException {
        final Issue issue = getIssue(taskKey);
        final Transition transition = getTransitionToOrThrow(issue, newState);
        final String key = issue.getKey();
        final String newStateLabel = newState.getLabel();
        logger.info("Transition issue {} to {}", key, newStateLabel);
        issue.transition().execute(transition);
        issue.refresh();

        if (!taskStateChangeNotificationQueues.isEmpty()) {
            // Send JMS Task State Notification
            sendJMSTaskStateChangeNotification(taskKey, newState, projectKey, key, newStateLabel, issue);
        }
    }

    private void sendJMSTaskStateChangeNotification(String taskKey, TaskStatus newState, String projectKey, String key, String newStateLabel, Issue issue) throws BusinessServiceException {
        try {
            Map<String, String> properties = new HashMap<>();
            properties.put("key", key);
            properties.put("status", newStateLabel);

            if (TaskStatus.COMPLETED.equals(newState)) {
                setCrsConceptsIfAny(projectKey, taskKey, properties);
            }

            // To comma separated list
            final String labelsString = issue.getLabels().toString();
            properties.put("labels", labelsString.substring(1, labelsString.length() - 1).replace(", ", ","));

            if (labelsString.contains(CRS_JIRA_LABEL)) {
                for (String queue : taskStateChangeNotificationQueues) {
                    if ((isIntAuthoringTask(projectKey, taskKey) && isIntTaskStateChangeQueue(queue))
                            || (!isIntAuthoringTask(projectKey, taskKey) && !isIntTaskStateChangeQueue(queue))) {
                        messagingHelper.send(new ActiveMQQueue(queue), properties);
                    }
                }
            }
        } catch (JsonProcessingException | JMSException e) {
            logger.error("Failed to send task state change notification for {} {}.", key, newStateLabel, e);
        }
    }

    private Transition getTransitionToOrThrow(Issue issue, TaskStatus newState)
            throws JiraException, BusinessServiceException {
        for (Transition transition : issue.getTransitions()) {
            if (transition.getToStatus().getName().equals(newState.getLabel())) {
                return transition;
            }
        }
        throw new BadRequestException("Could not transition task " + issue.getKey() + " from status '"
                + issue.getStatus().getName() + "' to '" + newState.name() + "', no such transition is available.");
    }

    @Override
    public List<TaskAttachment> getTaskAttachments(String projectKey, String taskKey) throws BusinessServiceException {
        List<TaskAttachment> attachments = new ArrayList<>();
        try {
            Issue issue = getIssue(taskKey);
            if (issue.getIssueLinks().isEmpty()) {
                boolean isIntTask = isIntAuthoringTask(projectKey, taskKey);
                ContentRequestServiceClient crsClient = contentRequestServiceClientFactory.getClient(uiConfiguration.getEndpoints().get(isIntTask ? "crsEndpoint" : "crsEndpoint.US"));
                List<Long> crsRequestIds = crsClient.findAcceptedRequestsByAuthoringTaskKey(taskKey);
                for (Long requestId : crsRequestIds) {
                    ContentRequestServiceClient.ContentRequestDto contentRequestDto = crsClient.getRequestDetails(String.valueOf(requestId));
                    String organization = contentRequestDto.getRequestHeader() != null ? contentRequestDto.getRequestHeader().getOrganization() : null;
                    TaskAttachment taskAttachment = new TaskAttachment(null, String.valueOf(requestId), contentRequestDto.getConcept().toString(), organization);
                    attachments.add(taskAttachment);
                }
            } else {
                for (IssueLink issueLink : issue.getIssueLinks()) {
                    getCrsJiraAttachment(issueLink.getOutwardIssue().getKey(), attachments, issue.getKey());
                }
            }
        } catch (JiraException e) {
            if (e.getCause() instanceof RestException restException && restException.getHttpStatusCode() == 404) {
                throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + toString(projectKey, taskKey), e);
            }
            throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
        }

        return attachments;
    }

    @Override
    public void getCrsJiraAttachment(String issueLinkKey, List<TaskAttachment> attachments, String issueKey) throws BusinessServiceException {
        // need to forcibly retrieve the issue in order to get
        // attachments

        final RestClient restClient = getJiraClient().getRestClient();
        Issue issue = null;
        try {
            issue = this.getIssue(issueLinkKey, true);
        } catch (JiraException e) {
            throw new BusinessServiceException(e);
        }
        Object organization = issue.getField(jiraOrganizationField);
        String crsId = issue.getField(jiraCrsIdField).toString();
        if (crsId == null) {
            crsId = "Unknown";
        }

        boolean attachmentFound = false;

        for (Attachment attachment : issue.getAttachments()) {

            if (attachment.getFileName().equals("request.json")) {

                // attachments must be retrieved by relative path --
                // absolute path will redirect to login
                try {
                    final String contentUrl = attachment.getContentUrl();
                    final JSON attachmentJson = restClient
                            .get(contentUrl.substring(contentUrl.indexOf("secure")));

                    TaskAttachment taskAttachment = new TaskAttachment(issue.getKey(), crsId, attachmentJson.toString(), organization != null ? organization.toString() : null);

                    attachments.add(taskAttachment);

                    attachmentFound = true;

                } catch (Exception e) {
                    throw new BusinessServiceException(
                            "Failed to retrieve attachment " + attachment.getContentUrl() + ": " + e.getMessage(), e);
                }
            }
        }

        // if no attachments, create a blank one to link CRS ticket id
        if (!attachmentFound) {
            TaskAttachment taskAttachment = new TaskAttachment(issueKey, crsId, null, organization != null ? organization.toString() : null);
            attachments.add(taskAttachment);
        }
    }

    @Override
    public void leaveCommentForTask(String projectKey, String taskKey, String comment) throws BusinessServiceException {
        try {
            addComment(taskKey, comment);
        } catch (JiraException e) {
            if (e.getCause() instanceof RestException restException && restException.getHttpStatusCode() == 404) {
                throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + toString(projectKey, taskKey), e);
            }
            throw new BusinessServiceException("Failed to leave comment for task " + toString(projectKey, taskKey), e);
        }
    }

    @Override
    public void removeCrsTaskForGivenRequestJiraKey(String issueKey, String linkId) throws BusinessServiceException {
        try {
            JiraHelper.deleteIssueLink(getJiraClient(), issueKey, linkId);
        } catch (JiraException e) {
            throw new BusinessServiceException(e);
        }
    }

    @Override
    public void removeCrsTaskForGivenRequestId(String projectKey, String taskKey, String crsId) {
        // do nothing
    }
}
