package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.AuthoringTask;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.Task;
import org.ihtsdo.authoringservices.entity.TaskReviewer;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.repository.TaskRepository;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class JiraAuthoringTaskMigrateService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskService taskService;

    @Async
    public void migrateJiraTask(Authentication authentication, List<AuthoringTask> authoringTasks) throws BusinessServiceException {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        List<Task> tasks = new ArrayList<>();
        for (AuthoringTask authoringTask : authoringTasks) {
            Optional<Project> optionalProject = projectRepository.findById(authoringTask.getProjectKey());
            if (optionalProject.isPresent()) {
                AuthoringTask authoringTaskWithDetails = taskService.retrieveTask(authoringTask.getProjectKey(), authoringTask.getKey(), true);
                Task task = new Task();
                task.setKey(authoringTaskWithDetails.getKey());
                task.setProject(optionalProject.get());
                task.setName(authoringTaskWithDetails.getSummary());
                task.setAssignee(authoringTaskWithDetails.getAssignee() != null ? authoringTaskWithDetails.getAssignee().getUsername() : null);
                task.setReporter(authoringTaskWithDetails.getReporter().getUsername());
                task.setStatus(authoringTaskWithDetails.getStatus());

                if (authoringTaskWithDetails.getReviewers() != null) {
                    List<TaskReviewer> reviewers = new ArrayList<>();
                    authoringTaskWithDetails.getReviewers().forEach(item -> reviewers.add(new TaskReviewer(task, item.getUsername())));
                    task.setReviewers(reviewers);
                }
                tasks.add(task);
            }
        }
        taskRepository.saveAll(tasks);
    }
}
