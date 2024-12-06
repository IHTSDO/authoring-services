package org.ihtsdo.authoringservices.service;

import jakarta.transaction.Transactional;
import org.ihtsdo.authoringservices.domain.AuthoringTask;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.Task;
import org.ihtsdo.authoringservices.entity.TaskReviewer;
import org.ihtsdo.authoringservices.entity.TaskSequence;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.repository.TaskRepository;
import org.ihtsdo.authoringservices.repository.TaskSequenceRepository;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class JiraAuthoringTaskMigrateService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskSequenceRepository taskSequenceRepository;

    @Autowired
    private TaskService taskService;

    @Async
    @Transactional
    public void migrateJiraTask(Authentication authentication, List<AuthoringTask> authoringTasks) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        Set<Task> tasks = new HashSet<>();
        Map<Project, Integer> projectToSequenceMap = new HashMap<>();
        for (AuthoringTask jiraTask : authoringTasks) {
            Optional<Project> optionalProject = projectRepository.findById(jiraTask.getProjectKey());
            if (optionalProject.isEmpty()) continue;

            try {
                Project project = optionalProject.get();
                AuthoringTask jiraTaskWithDetails = taskService.retrieveTask(jiraTask.getProjectKey(), jiraTask.getKey(), true);
                Optional<Task> existingTaskOptional = taskRepository.findById(jiraTaskWithDetails.getKey());
                Task task = existingTaskOptional.orElseGet(Task::new);
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
                tasks.add(task);
                int taskSequence = getTaskSequence(jiraTaskWithDetails.getKey());
                if (!(projectToSequenceMap.containsKey(project) && projectToSequenceMap.get(project) >= taskSequence)) {
                    projectToSequenceMap.put(project, taskSequence);
                }
            } catch (BusinessServiceException e) {
                logger.error(e.getMessage());
            }
        }

        taskRepository.saveAll(tasks);
        updateTaskSequence(projectToSequenceMap);
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
}
