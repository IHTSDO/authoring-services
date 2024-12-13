package org.ihtsdo.authoringservices.service;

import jakarta.transaction.Transactional;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.AuthoringTask;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.Task;
import org.ihtsdo.authoringservices.entity.TaskReviewer;
import org.ihtsdo.authoringservices.entity.TaskSequence;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.repository.TaskRepository;
import org.ihtsdo.authoringservices.repository.TaskSequenceRepository;
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
    private TaskSequenceRepository taskSequenceRepository;

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
        Map<Project, Integer> projectToSequenceMap = new HashMap<>();
        for (Project project : projectIterable) {
            TimerUtil timer = new TimerUtil("Migrate Jira Task for project " + project.getKey(), Level.DEBUG);
            List<Issue> issues;
            try {
                issues = listAllJiraTasksForProject(project.getKey());
                for (Issue issue : issues) {
                    migrateJiraTask(project, issue, tasks, projectToSequenceMap);
                }
            } catch (BusinessServiceException e) {
                logger.error(e.getMessage());
            }
            timer.finish();
        }

        taskRepository.saveAll(tasks);
        updateTaskSequence(projectToSequenceMap);
    }

    private void migrateJiraTask(Project project, Issue issue, Set<Task> tasks, Map<Project, Integer> projectToSequenceMap) {
        try {
            Optional<Task> existingTaskOptional = taskRepository.findById(issue.getKey());
            if (existingTaskOptional.isPresent()) return;

            AuthoringTask jiraTaskWithDetails = jiraTaskService.retrieveTask(project.getKey(), issue.getKey(), true);
            Task task = getNewTask(project, jiraTaskWithDetails);
            tasks.add(task);
            int taskSequence = getTaskSequence(jiraTaskWithDetails.getKey());
            if (!(projectToSequenceMap.containsKey(project) && projectToSequenceMap.get(project) >= taskSequence)) {
                projectToSequenceMap.put(project, taskSequence);
            }
        } catch (BusinessServiceException e) {
            logger.error(e.getMessage());
        } catch (ParseException e) {
            logger.error("Failed to parse date. Message: {}", e.getMessage());
        }
    }

    @NotNull
    private Task getNewTask(Project project, AuthoringTask jiraTaskWithDetails) throws ParseException {
        Task task = new Task();
        task.setKey(jiraTaskWithDetails.getKey());
        task.setProject(project);
        task.setName(jiraTaskWithDetails.getSummary());
        task.setAssignee(jiraTaskWithDetails.getAssignee() != null ? jiraTaskWithDetails.getAssignee().getUsername() : null);
        task.setReporter(jiraTaskWithDetails.getReporter().getUsername());
        task.setStatus(jiraTaskWithDetails.getStatus());
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

    private void updateTaskSequence(Map<Project, Integer> projectToSequenceMap) {
        if (!projectToSequenceMap.isEmpty()) {
            Set<TaskSequence> sequences = new HashSet<>();
            projectToSequenceMap.forEach((key, value) -> {
                TaskSequence existing = taskSequenceRepository.findOneByProject(key);
                if (existing != null) {
                    existing.setSequence(value);
                    sequences.add(existing);
                } else {
                    sequences.add(new TaskSequence(key, value));
                }
            });
            taskSequenceRepository.saveAll(sequences);
        }
    }

    private int getTaskSequence(String key) throws BusinessServiceException {
        String[] split = key.split("-");
        if (split.length != 0) {
            return Integer.parseInt(split[split.length - 1]);
        }

        throw new BusinessServiceException("Could not determine the task sequence from key: " + key);
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
