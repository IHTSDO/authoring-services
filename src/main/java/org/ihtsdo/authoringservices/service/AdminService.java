package org.ihtsdo.authoringservices.service;

import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
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
    public List<JSONObject> retrieveProjectCustomFields(AuthoringProject project) throws BusinessServiceException {
        return projectService.retrieveProjectCustomFields(project.getKey());
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #codeSystem.branchPath)")
    public AuthoringProject createProject(AuthoringCodeSystem codeSystem, CreateProjectRequest request) throws BusinessServiceException, ServiceException {
        logger.info("Creating new project {} on code system {}", request.key(), codeSystem.getShortName());
        AuthoringProject project = projectService.createProject(request);
        String projectBranchPath = codeSystem.getBranchPath() + SLASH + request.key();
        branchService.createProjectBranchIfNeeded(projectBranchPath);
        project.setBranchPath(projectBranchPath);
        return project;
    }
}
