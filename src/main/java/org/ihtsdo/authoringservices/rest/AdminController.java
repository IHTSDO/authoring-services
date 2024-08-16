package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<String> deleteTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws JiraException, BusinessServiceException {
        AuthoringProject project = taskService.retrieveProject(projectKey, true);
        adminService.deleteIssue(project.getCodeSystem(), taskKey);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
