package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.AdminService;
import org.ihtsdo.authoringservices.service.CodeSystemService;
import org.ihtsdo.authoringservices.service.JiraAuthoringTaskMigrateService;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.EntityAlreadyExistsException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@Tag(name = "Admin")
@RequestMapping(value = "/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private ProjectServiceFactory projectServiceFactory;

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private JiraAuthoringTaskMigrateService jiraAuthoringTaskMigrateService;

    @PreAuthorize("hasPermission('ADMIN', 'global')")
    @Operation(summary = "Migrate jira CRS tasks")
    @PostMapping(value = "/migrate-jira-crs-tasks")
    public void migrateJiraCrsTasksManual(@RequestParam(required = false) Set<String> projectKeys) {
        jiraAuthoringTaskMigrateService.migrateJiraCrsTasks(projectKeys);
    }

    @Operation(summary = "Create a task within a project")
    @PostMapping(value = "/projects/{projectKey}/tasks")
    public AuthoringTask createTask(@PathVariable final String projectKey, @RequestParam(value = "useNew", required = false) Boolean useNew,
                                    @RequestParam(value = "type", required = false, defaultValue = "AUTHORING") TaskType type,
                                    @RequestBody final AuthoringTaskCreateRequest taskCreateRequest) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        return adminService.createTask(project, taskCreateRequest, useNew, type);
    }

    @Operation(summary = "Delete a given task key", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}/tasks/{taskKey}")
    public ResponseEntity<Void> deleteTask(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestParam(value = "useNew", required = false) Boolean useNew) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        adminService.deleteTask(project, taskKey, useNew);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Bulk-delete multiple task keys", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}/tasks/bulk-delete")
    public ResponseEntity<Void> deleteTasks(@PathVariable final String projectKey,
                                            @RequestParam(value = "useNew", required = false) Boolean useNew,
                                            @Parameter(description = "Task keys") @RequestParam final List<String> taskKeys) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        adminService.deleteTasks(project, new HashSet<>(taskKeys), useNew);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Create a new project", description = "-")
    @PostMapping(value = "/projects")
    public ResponseEntity<AuthoringProject> createProject(@RequestParam(value = "useNew", required = false) Boolean useNew, @RequestBody CreateProjectRequest request) throws BusinessServiceException, RestClientException, ServiceException {
        findProjectAndThrowIfExists(request.key(), useNew);
        AuthoringCodeSystem codeSystem = codeSystemService.findOne(request.codeSystemShortName());
        return new ResponseEntity<>(adminService.createProject(codeSystem, request, useNew), HttpStatus.CREATED);
    }

    @Operation(summary = "Retrieve all custom fields for a given project", description = "-")
    @GetMapping(value = "/projects/{projectKey}/custom-field")
    public ResponseEntity<List<AuthoringProjectField>> getProjectCustomFields(@PathVariable final String projectKey, @RequestParam(value = "useNew", required = false) Boolean useNew) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        return new ResponseEntity<>(adminService.retrieveProjectCustomFields(project, useNew), HttpStatus.OK);
    }

    @Operation(summary = "Update custom fields for a given project", description = "-")
    @PutMapping(value = "/projects/{projectKey}/custom-field")
    public ResponseEntity<Void> updateProjectCustomFields(@PathVariable final String projectKey, @RequestBody ProjectFieldUpdateRequest request, @RequestParam(value = "useNew", required = false) Boolean useNew) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        adminService.updateProjectCustomFields(project, request, useNew);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Retrieve all roles for a given project", description = "-")
    @GetMapping(value = "/projects/{projectKey}/role")
    public ResponseEntity<List<String>> getProjectRoles(@PathVariable final String projectKey, @RequestParam(value = "useNew", required = false) Boolean useNew) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        return new ResponseEntity<>(adminService.retrieveProjectRoles(project, useNew), HttpStatus.OK);
    }

    @Operation(summary = "Update roles for a given project", description = "-")
    @PutMapping(value = "/projects/{projectKey}/role")
    public ResponseEntity<Void> updateProjectRoles(@PathVariable final String projectKey, @RequestBody ProjectRoleUpdateRequest request, @RequestParam(value = "useNew", required = false) Boolean useNew) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        adminService.updateProjectRoles(project, request, useNew);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Delete a given project key", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}")
    public ResponseEntity<Void> deleteProject(@PathVariable final String projectKey, @RequestParam(value = "useNew", required = false) Boolean useNew) throws BusinessServiceException {
        AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        adminService.deleteProject(project, useNew);
        return new ResponseEntity<>(HttpStatus.OK);
    }


    @Operation(summary = "Bulk-delete multiple project keys", description = "-")
    @DeleteMapping(value = "/projects/bulk-delete")
    public ResponseEntity<Void> deleteProjects(@Parameter(description = "Project keys") @RequestParam final List<String> projectKeys, @RequestParam(value = "useNew", required = false) Boolean useNew) throws BusinessServiceException {
        for (String projectKey : projectKeys) {
            AuthoringProject project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
            adminService.deleteProject(project, useNew);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void findProjectAndThrowIfExists(String projectKey, Boolean useNew) throws EntityAlreadyExistsException {
        AuthoringProject project = null;
        try {
            project = projectServiceFactory.getInstance(useNew).retrieveProject(projectKey, true);
        } catch (BusinessServiceException | ResourceNotFoundException e) {
            // do nothing
        }
        if (project != null) {
            throw new EntityAlreadyExistsException(String.format("Project with key %s already exists", projectKey));
        }
    }
}
