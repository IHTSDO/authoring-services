package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
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

    @Autowired
    private BranchService branchService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public AuthoringTask createTask(AuthoringProject project, AuthoringTaskCreateRequest taskCreateRequest) throws BusinessServiceException {
        String assignee = taskCreateRequest.getAssignee() != null ? taskCreateRequest.getAssignee().getUsername() : SecurityUtil.getUsername();
        return taskService.createTask(project.getKey(), assignee, taskCreateRequest);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteTask(AuthoringProject project, String issueKey) throws BusinessServiceException {
        logger.info("Deleting task with key {} on project {}", issueKey, project.getKey());
        taskService.deleteTask(issueKey);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteTasks(AuthoringProject project, Set<String> taskKeys) throws BusinessServiceException {
        logger.info("Deleting tasks [{}] on project {}", taskKeys, project.getKey());
        taskService.deleteTasks(taskKeys);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteProject(AuthoringProject project) throws BusinessServiceException {
        logger.info("Deleting project {}", project.getKey());
        projectService.deleteProject(project.getKey());
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public List<AuthoringProjectField> retrieveProjectCustomFields(AuthoringProject project) throws BusinessServiceException {
        return projectService.retrieveProjectCustomFields(project.getKey());
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #codeSystem.branchPath)")
    public AuthoringProject createProject(AuthoringCodeSystem codeSystem, CreateProjectRequest request) throws BusinessServiceException, ServiceException {
        logger.info("Creating new project {} on code system {}", request.key(), codeSystem.getShortName());
        AuthoringProject project = projectService.createProject(request, codeSystem.getBranchPath());
        String projectBranchPath = codeSystem.getBranchPath() + SLASH + request.key();
        branchService.createBranchIfNeeded(projectBranchPath);
        project.setBranchPath(projectBranchPath);
        return project;
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void updateProjectCustomFields(AuthoringProject project, ProjectFieldUpdateRequest request) throws BusinessServiceException {
        logger.info("Updating custom fields for project {}", project.getKey());
        projectService.updateProjectCustomFields(project.getKey(), request);
    }
}
