package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.Project;
import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.ihtsdo.authoringservices.service.AdminService;
import org.ihtsdo.authoringservices.service.CodeSystemService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.EntityAlreadyExistsException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;

@RestController
@Tag(name = "Admin")
@RequestMapping(value = "/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private CodeSystemService codeSystemService;

    @Operation(summary = "Delete a given task key", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}/tasks/{taskKey}")
    public ResponseEntity<Void> deleteTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws JiraException, BusinessServiceException {
        AuthoringProject project = taskService.retrieveProject(projectKey, true);
        adminService.deleteIssue(project, taskKey);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Bulk-delete multiple task keys", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}/tasks/bulk-delete")
    public ResponseEntity<Void> deleteTasks(@PathVariable final String projectKey,
                                            @Parameter(description = "Task keys") @RequestParam final List<String> taskKeys) throws JiraException, BusinessServiceException {
        AuthoringProject project = taskService.retrieveProject(projectKey, true);
        adminService.deleteIssues(project, new HashSet<>(taskKeys));
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Create a new project", description = "-")
    @PostMapping(value = "/projects")
    public ResponseEntity<Project> createProject(@RequestBody CreateProjectRequest request) throws BusinessServiceException, RestClientException, ServiceException, JiraException {
        findProjectAndThrowIfExists(request.key());
        AuthoringCodeSystem codeSystem = codeSystemService.findOne(request.codeSystemShortName());
        return new ResponseEntity<>(adminService.createProject(codeSystem, request), HttpStatus.CREATED);
    }

    @Operation(summary = "Delete a given project key", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}")
    public ResponseEntity<Void> deleteProject(@PathVariable final String projectKey) throws JiraException, BusinessServiceException {
        AuthoringProject project = taskService.retrieveProject(projectKey, true);
        adminService.deleteProject(project);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Bulk-delete multiple project keys", description = "-")
    @DeleteMapping(value = "/projects/bulk-delete")
    public ResponseEntity<Void> deleteProjects(@Parameter(description = "Project keys") @RequestParam final List<String> projectKeys) throws JiraException, BusinessServiceException {
        for (String projectKey : projectKeys) {
            AuthoringProject project = taskService.retrieveProject(projectKey, true);
            adminService.deleteProject(project);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void findProjectAndThrowIfExists(String projectKey) {
        try {
            taskService.retrieveProject(projectKey, true);
            throw new EntityAlreadyExistsException(String.format("Project with key %s already exists", projectKey));
        } catch (BusinessServiceException | ResourceNotFoundException e) {
            // do nothing
        }
    }
}
