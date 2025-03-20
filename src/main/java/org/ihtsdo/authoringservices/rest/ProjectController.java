package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Tag(name = "Authoring Projects")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class ProjectController {

    private static final String REBASING_STATUS = "Rebasing";

    @Autowired
    private ProjectServiceFactory projectServiceFactory;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private RebaseService rebaseService;

    @Autowired
    private ScheduledRebaseService scheduledRebaseService;

    @Autowired
    private BranchService branchService;

    @Operation(summary = "Retrieve Authoring Info (Code System, Project and Task information) for a given branch")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/branches/{branch}/authoring-info")
    public ResponseEntity<AuthoringInfoWrapper> getBranchAuthoringInformation(@PathVariable String branch) throws BusinessServiceException, ServiceException, RestClientException {
        String branchPath = BranchPathUriUtil.decodePath(branch);
        return new ResponseEntity<>(branchService.getBranchAuthoringInfoWrapper(branchPath), HttpStatus.OK);
    }

    @Operation(summary = "List authoring projects")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects")
    public List<AuthoringProject> listProjects(@RequestParam(value = "lightweight", required = false) Boolean lightweight,
                                               @RequestParam(value = "ignoreProductCodeFilter", required = false) Boolean ignoreProductCodeFilter,
                                               @Parameter(description = "Project type (Possible values are: <b>CRS, ALL</b>)") @RequestParam(value = "type", required = false) String type) throws BusinessServiceException {
        List<AuthoringProject> results = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(lightweight, ignoreProductCodeFilter, type));
        List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(lightweight, ignoreProductCodeFilter, type);
        return filterJiraProjects(jiraProjects, results);
    }

    @Operation(summary = "Retrieve an authoring project")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}")
    public AuthoringProject retrieveProject(@PathVariable final String projectKey) throws BusinessServiceException {
        return projectServiceFactory.getInstanceByKey(requiredParam(projectKey, PROJECT_KEY)).retrieveProject(requiredParam(projectKey, PROJECT_KEY));
    }

    @Operation(summary = "Rebase an authoring project")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/rebase")
    public ResponseEntity<String> rebaseProject(@PathVariable final String projectKey) throws BusinessServiceException {
        ProcessStatus processStatus = rebaseService.getProjectRebaseStatus(requiredParam(projectKey, PROJECT_KEY));
        if (processStatus == null || !REBASING_STATUS.equals(processStatus.getStatus())) {
            rebaseService.doProjectRebase(null, projectKey);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Get rebase status of an authoring project")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/rebase/status")
    public ProcessStatus getProjectRebaseStatus(@PathVariable final String projectKey) {
        return rebaseService.getProjectRebaseStatus(requiredParam(projectKey, PROJECT_KEY));
    }

    @Operation(summary = "Promote an authoring project")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/promote")
    public ResponseEntity<String> promoteProject(@PathVariable final String projectKey, @RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
        ProcessStatus processStatus = promotionService.getProjectPromotionStatus(requiredParam(projectKey, PROJECT_KEY));
        if (processStatus == null || !REBASING_STATUS.equals(processStatus.getStatus())) {
            promotionService.doProjectPromotion(projectKey, mergeRequest);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Retrieve status information about project promotion")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/promote/status")
    public ProcessStatus getProjectPromotionStatus(@PathVariable final String projectKey) {
        return promotionService.getProjectPromotionStatus(requiredParam(projectKey, PROJECT_KEY));
    }

    @Operation(summary = "Update a project")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping(value = "/projects/{projectKey}")
    public AuthoringProject updateProject(@PathVariable final String projectKey, @RequestBody final AuthoringProject updatedProject) throws BusinessServiceException {
        return projectServiceFactory.getInstanceByKey(projectKey).updateProject(requiredParam(projectKey, PROJECT_KEY), updatedProject);
    }

    @Operation(
            summary = "Manual trigger for scheduled project rebase process.",
            description = "This endpoint is asynchronous so will return straight away.")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/auto-rebase")
    public ResponseEntity<String> autoRebaseProjects() throws BusinessServiceException {
        scheduledRebaseService.rebaseProjectsManualTrigger();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Lock project")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/lock")
    public void lockProject(@PathVariable final String projectKey) throws BusinessServiceException {
        projectServiceFactory.getInstanceByKey(projectKey).lockProject(projectKey);
    }

    @Operation(summary = "Unlock project")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/unlock")
    public void unlockProject(@PathVariable final String projectKey) throws BusinessServiceException {
        projectServiceFactory.getInstanceByKey(projectKey).unlockProject(projectKey);
    }

    private List<AuthoringProject> filterJiraProjects(List<AuthoringProject> jiraProjects, List<AuthoringProject> authoringProjects) {
        if (jiraProjects.isEmpty()) return authoringProjects;
        List<String> authoringProjectKeys = new ArrayList<>(authoringProjects.stream().map(AuthoringProject::getKey).toList());
        for (AuthoringProject project : jiraProjects) {
            if (!authoringProjectKeys.contains(project.getKey())) {
                authoringProjects.add(project);
                authoringProjectKeys.add(project.getKey());
            }
        }
        return authoringProjects;
    }
}