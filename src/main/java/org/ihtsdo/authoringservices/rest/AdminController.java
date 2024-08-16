package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.service.AdminService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
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

    @Operation(summary = "Delete a given task key", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}/tasks/{taskKey}")
    public ResponseEntity<Void> deleteTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws JiraException, BusinessServiceException {
        AuthoringProject project = taskService.retrieveProject(projectKey, true);
        adminService.deleteIssue(project, taskKey);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Delete a given task key", description = "-")
    @DeleteMapping(value = "/projects/{projectKey}/tasks/bulk-delete")
    public ResponseEntity<Void> deleteTasks(@PathVariable final String projectKey,
                                              @Parameter(description = "Task keys") @RequestParam final List<String> taskKeys) throws JiraException, BusinessServiceException {
        AuthoringProject project = taskService.retrieveProject(projectKey, true);
        adminService.deleteIssues(project, new HashSet<>(taskKeys));
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
