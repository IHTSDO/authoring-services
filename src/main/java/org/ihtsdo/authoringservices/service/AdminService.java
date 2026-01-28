package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AdminService {

    private final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private static final String SLASH = "/";

    private final BranchService branchService;

    private final ProjectServiceFactory projectServiceFactory;

    private final TaskServiceFactory taskServiceFactory;

    @Autowired
    public AdminService(BranchService branchService, ProjectServiceFactory projectServiceFactory, TaskServiceFactory taskServiceFactory) {
        this.branchService =branchService;
        this.projectServiceFactory = projectServiceFactory;
        this.taskServiceFactory = taskServiceFactory;
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public AuthoringTask createTask(AuthoringProject project, AuthoringTaskCreateRequest taskCreateRequest, Boolean useNew, TaskType type) throws BusinessServiceException {
        String assignee = taskCreateRequest.getAssignee() != null ? taskCreateRequest.getAssignee().getUsername() : SecurityUtil.getUsername();
        return taskServiceFactory.getInstance(useNew).createTask(project.getKey(), assignee, taskCreateRequest, type);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteTasks(AuthoringProject project, Set<String> taskKeys, Boolean useNew) throws BusinessServiceException {
        logger.info("Deleting tasks [{}] on project {}", taskKeys, project.getKey());
        taskServiceFactory.getInstance(useNew).deleteTasks(taskKeys);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteProject(AuthoringProject project, Boolean useNew) throws BusinessServiceException {
        logger.info("Deleting project {}", project.getKey());
        projectServiceFactory.getInstance(useNew).deleteProject(project.getKey());
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public List<AuthoringProjectField> retrieveProjectCustomFields(AuthoringProject project, Boolean useNew) throws BusinessServiceException {
        return projectServiceFactory.getInstance(useNew).retrieveProjectCustomFields(project.getKey());
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #codeSystem.branchPath)")
    public AuthoringProject createProject(AuthoringCodeSystem codeSystem, CreateProjectRequest request, Boolean useNew) throws BusinessServiceException, ServiceException {
        logger.info("Creating new project {} on code system {}", request.key(), codeSystem.getShortName());
        AuthoringProject project = projectServiceFactory.getInstance(useNew).createProject(request, codeSystem);
        String projectBranchPath = codeSystem.getBranchPath() + SLASH + request.key();
        branchService.createBranchIfNeeded(projectBranchPath);
        project.setBranchPath(projectBranchPath);
        return project;
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void updateProjectCustomFields(AuthoringProject project, ProjectFieldUpdateRequest request, Boolean useNew) throws BusinessServiceException {
        logger.info("Updating custom fields for project {}", project.getKey());
        projectServiceFactory.getInstance(useNew).updateProjectCustomFields(project.getKey(), request);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public List<String> retrieveProjectRoles(AuthoringProject project, Boolean useNew) throws BusinessServiceException {
        return projectServiceFactory.getInstance(useNew).retrieveProjectRoles(project.getKey());
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void updateProjectRoles(AuthoringProject project, ProjectRoleUpdateRequest request, Boolean useNew) throws BusinessServiceException {
        logger.info("Updating roles for project {}", project.getKey());
        projectServiceFactory.getInstance(useNew).updateProjectRoles(project.getKey(), request);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global')")
    public void markTasksAsDeleted(Set<String> taskKeys) {
        final AuthoringTask request = new AuthoringTask();
        request.setStatus(TaskStatus.DELETED);
        taskKeys.forEach(key -> {
            try {
                AuthoringTask internalAuthoringTask = taskServiceFactory.getInstance(true).retrieveTask(null, key, true, true);
                taskServiceFactory.getInstance(true).updateTask(internalAuthoringTask.getProjectKey(), internalAuthoringTask.getKey(), request);
            } catch (ResourceNotFoundException | BusinessServiceException e) {
                // Do nothing
            }

            try {
                AuthoringTask jiraAuthoringTask = taskServiceFactory.getInstance(false).retrieveTask(null, key, true, true);
                taskServiceFactory.getInstance(false).updateTask(jiraAuthoringTask.getProjectKey(), jiraAuthoringTask.getKey(), request);
            } catch (ResourceNotFoundException | BusinessServiceException | UnsupportedOperationException e) {
                // Do nothing
            }
        });
    }

    public void updateProjectStatus(String projectKey, Boolean useNew, Boolean activeStatus) throws BusinessServiceException {
        logger.info("Updating active status for project {} to {}", projectKey, activeStatus);
        
        // Create an updated project with the new active status
        AuthoringProject updatedProject = new AuthoringProject();
        updatedProject.setActive(activeStatus);
        
        // Update the project
        projectServiceFactory.getInstance(useNew).updateProject(projectKey, updatedProject);
        
        logger.info("Project {} active status updated to {}", projectKey, activeStatus);
    }
}
