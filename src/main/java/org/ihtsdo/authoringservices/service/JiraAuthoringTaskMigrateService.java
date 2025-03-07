package org.ihtsdo.authoringservices.service;

import jakarta.transaction.Transactional;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.AuthoringTask;
import org.ihtsdo.authoringservices.domain.TaskStatus;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.Task;
import org.ihtsdo.authoringservices.entity.TaskReviewer;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.repository.TaskRepository;
import org.ihtsdo.authoringservices.service.impl.TaskServiceBase;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.util.TimerUtil;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

import static org.ihtsdo.authoringservices.service.impl.JiraProjectServiceImpl.LIMIT_UNLIMITED;

@Service
public class JiraAuthoringTaskMigrateService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskService jiraTaskService;

    @Autowired
    @Qualifier("authoringTaskOAuthJiraClient")
    private ImpersonatingJiraClientFactory jiraClientFactory;

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    @Transactional
    public void migrateJiraTasks(Set<String> projectKeys) {
        Iterable<Project> projectIterable = CollectionUtils.isEmpty(projectKeys) ? projectRepository.findAll() : projectRepository.findAllById(projectKeys);
        Set<Task> tasks = new HashSet<>();
        for (Project project : projectIterable) {
            try {
                TimerUtil timer = new TimerUtil("Migrate Jira Task for project " + project.getKey(), Level.INFO);
                List<Issue> issues = listAllJiraTasksForProject(project.getKey());
                for (Issue issue : issues) {
                    migrateJiraTask(project, issue, tasks);
                }
                timer.finish();
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }

        taskRepository.saveAll(tasks);
    }

    private void migrateJiraTask(Project project, Issue issue, Set<Task> tasks) {
        try {
            Optional<Task> existingTaskOptional = taskRepository.findById(issue.getKey());
            if (existingTaskOptional.isPresent() || TaskStatus.DELETED.equals(TaskStatus.fromLabel(issue.getStatus().getName()))) return;

            AuthoringTask jiraTaskWithDetails = jiraTaskService.retrieveTask(project.getKey(), issue.getKey(), true, true);
            if (jiraTaskWithDetails.getLabels() != null && jiraTaskWithDetails.getLabels().contains(TaskServiceBase.CRS_JIRA_LABEL)) return;

            Task task = getNewTask(project, jiraTaskWithDetails);
            tasks.add(task);
        } catch (BusinessServiceException e) {
            logger.error(e.getMessage());
        } catch (ParseException e) {
            logger.error("Failed to parse date. Message: {}", e.getMessage());
        }
    }

    @NotNull
    private Task getNewTask(Project project, AuthoringTask jiraTaskWithDetails) throws ParseException, BusinessServiceException {
        Task task = new Task();
        task.setKey(jiraTaskWithDetails.getKey());
        task.setProject(project);
        task.setName(jiraTaskWithDetails.getSummary());
        task.setAssignee(jiraTaskWithDetails.getAssignee() != null ? jiraTaskWithDetails.getAssignee().getUsername() : null);
        task.setReporter(jiraTaskWithDetails.getReporter().getUsername());
        task.setDescription(jiraTaskWithDetails.getDescription());
        task.setStatus(jiraTaskWithDetails.getStatus());
        task.setBranchPath(project.getBranchPath() + "/" + jiraTaskWithDetails.getKey());
        if (jiraTaskWithDetails.getReviewers() != null) {
            List<TaskReviewer> existing = getTaskReviewers(jiraTaskWithDetails, task);
            task.setReviewers(existing);
        }
        task.setCreatedDate(getTimestamp(jiraTaskWithDetails.getCreated()));
        task.setUpdatedDate(getTimestamp(jiraTaskWithDetails.getUpdated()));
        return task;
    }

    private Timestamp getTimestamp(String date) throws ParseException {
        return Timestamp.from(Instant.ofEpochMilli(formatter.parse(date).getTime()));
    }

    private List<TaskReviewer> getTaskReviewers(AuthoringTask jiraTaskWithDetails, Task task) {
        List<TaskReviewer> existing = Objects.requireNonNullElseGet(task.getReviewers(), ArrayList::new);
        List<String> reviewers = jiraTaskWithDetails.getReviewers().stream().map(User::getUsername).toList();
        reviewers.forEach(item -> {
            TaskReviewer found = existing.stream().filter(e -> e.getUsername().equals(item)).findFirst().orElse(null);
            if (found == null) {
                existing.add(new TaskReviewer(task, item));
            }
        });
        existing.removeIf(item -> !reviewers.contains(item.getUsername()));
        return existing;
    }

    private List<Issue> listAllJiraTasksForProject(String projectKey) throws BusinessServiceException {
        List<Issue> issues;
        try {
            String jql = "project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\"";
            issues = searchIssuesWithJiraAdmin(jql);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to list tasks.", e);
        }
        return issues;
    }

    private List<Issue> searchIssuesWithJiraAdmin(String jql) throws JiraException {
        List<Issue> issues = new ArrayList<>();
        Issue.SearchResult searchResult;
        do {
            searchResult = jiraClientFactory.getAdminInstance().searchIssues(jql, null, LIMIT_UNLIMITED - issues.size(), issues.size());
            issues.addAll(searchResult.issues);
        } while (searchResult.total > issues.size());

        return issues;
    }
}
