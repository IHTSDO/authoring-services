package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Tag(name = "Authoring Projects")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class ProjectController {

    private static final String REBASING_STATUS = "Rebasing";

    @Autowired
    private JiraAuthoringTaskMigrateService migrateJiraTaskService;

    @Autowired
    private TaskServiceFactory taskServiceFactory;

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
                                               @RequestParam(value = "ignoreProductCodeFilter", required = false) Boolean ignoreProductCodeFilter) throws BusinessServiceException {
        List<AuthoringProject> results = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(lightweight, ignoreProductCodeFilter));
        List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(lightweight, ignoreProductCodeFilter);
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
            rebaseService.doProjectRebase(projectKey);
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

    @Operation(summary = "Retrieve status information about the MAIN branch")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/main")
    public AuthoringMain retrieveMain() throws BusinessServiceException {
        return taskServiceFactory.getInstance(true).retrieveMain();
    }

    @Operation(summary = "List tasks within a project")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/tasks")
    public List<AuthoringTask> listTasks(@PathVariable final String projectKey, @RequestParam(value = "lightweight", required = false) Boolean lightweight) throws BusinessServiceException {
        boolean useNew = projectServiceFactory.getInstance(true).isUseNew(projectKey);
        return taskServiceFactory.getInstance(useNew).listTasksForProject(requiredParam(projectKey, PROJECT_KEY), lightweight);
    }

    @Operation(summary = "List authenticated user's tasks across projects")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/my-tasks")
    public List<AuthoringTask> listMyTasks(@RequestParam(value = "excludePromoted", required = false) String excludePromoted) throws BusinessServiceException {
        List<AuthoringTask> results = new ArrayList<>(taskServiceFactory.getInstance(true).listMyTasks(SecurityUtil.getUsername(), excludePromoted));
        List<AuthoringTask> jiraTasks = taskServiceFactory.getInstance(false).listMyTasks(SecurityUtil.getUsername(), excludePromoted);
        return filterJiraTasks(jiraTasks, results);
    }

    @Operation(summary = "List review tasks, with the current user or unassigned reviewer, across projects")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/review-tasks")
    public List<AuthoringTask> listMyOrUnassignedReviewTasks(@RequestParam(value = "excludePromoted", required = false) String excludePromoted) throws BusinessServiceException {
        List<AuthoringTask> results = new ArrayList<>(taskServiceFactory.getInstance(true).listMyOrUnassignedReviewTasks(excludePromoted));
        List<AuthoringTask> jiraTasks = taskServiceFactory.getInstance(false).listMyOrUnassignedReviewTasks(excludePromoted);
        return filterJiraTasks(jiraTasks, results);
    }

    @Operation(summary = "Search tasks across projects")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/tasks/search")
    public List<AuthoringTask> searchTasks(@RequestParam(value = "criteria") String criteria, @RequestParam(value = "lightweight", required = false) Boolean lightweight) throws BusinessServiceException {
        List<AuthoringTask> results = new ArrayList<>(taskServiceFactory.getInstance(true).searchTasks(criteria, lightweight));
        List<AuthoringTask> jiraTasks = taskServiceFactory.getInstance(false).searchTasks(criteria, lightweight);
        return filterJiraTasks(jiraTasks, results);
    }

    @Operation(summary = "Retrieve a task within a project")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}")
    public AuthoringTask retrieveTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
        return taskServiceFactory.getInstanceByKey(taskKey).retrieveTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), false);
    }

    @Operation(summary = "Create a task within a project")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/tasks")
    public AuthoringTask createTask(@PathVariable final String projectKey, @RequestBody final AuthoringTaskCreateRequest taskCreateRequest) throws BusinessServiceException {
        boolean useNew = projectServiceFactory.getInstance(true).isUseNew(projectKey);
        return taskServiceFactory.getInstance(useNew).createTask(requiredParam(projectKey, PROJECT_KEY), SecurityUtil.getUsername(), taskCreateRequest);
    }

    @Operation(summary = "Update a task")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping(value = "/projects/{projectKey}/tasks/{taskKey}")
    public AuthoringTask updateTask(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestBody final AuthoringTaskUpdateRequest updatedTask) throws BusinessServiceException {
        return taskServiceFactory.getInstanceByKey(taskKey).updateTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), updatedTask);
    }

    @Operation(summary = "Update a project")
    @ApiResponse(responseCode = "200", description = "OK")
    @PutMapping(value = "/projects/{projectKey}")
    public AuthoringProject updateProject(@PathVariable final String projectKey, @RequestBody final AuthoringProject updatedProject) throws BusinessServiceException {
        return projectServiceFactory.getInstanceByKey(projectKey).updateProject(requiredParam(projectKey, PROJECT_KEY), updatedProject);
    }

    @Operation(summary = "Retrieve task attachments")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/attachments")
    public List<TaskAttachment> getAttachmentsForTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
        return taskServiceFactory.getInstanceByKey(taskKey).getTaskAttachments(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
    }

    @Operation(summary = "Leave comment for a task")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/comment")
    public void leaveComment(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestBody final String comment) throws BusinessServiceException {
        taskServiceFactory.getInstanceByKey(taskKey).leaveCommentForTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), comment);
    }

    @Operation(summary = "Rebase an authoring task")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/rebase")
    public ResponseEntity<String> rebaseTask(@PathVariable final String projectKey, @PathVariable final String taskKey) {
        ProcessStatus processStatus = rebaseService.getTaskRebaseStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
        if (processStatus == null || !REBASING_STATUS.equals(processStatus.getStatus())) {
            rebaseService.doTaskRebase(projectKey, taskKey);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Get rebase status of an authoring task")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/rebase/status")
    public ProcessStatus getRebaseTaskStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
        return rebaseService.getTaskRebaseStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
    }

    @Operation(summary = "Promote an authoring task")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/promote")
    public ResponseEntity<String> promoteTask(@PathVariable final String projectKey,
                                              @PathVariable final String taskKey,
                                              @RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
        ProcessStatus processStatus = promotionService.getTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
        if (processStatus == null || !REBASING_STATUS.equals(processStatus.getStatus())) {
            promotionService.doTaskPromotion(projectKey, taskKey, mergeRequest);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Get status of authoring task promotion.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/promote/status")
    public ProcessStatus getTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
        return promotionService.getTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
    }

    @Operation(summary = "Auto-promote an authoring task")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/auto-promote")
    public ResponseEntity<String> autoPromoteTask(@PathVariable final String projectKey, @PathVariable final String taskKey) {
        ProcessStatus currentProcessStatus = promotionService.getAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
        if (!(null != currentProcessStatus && (REBASING_STATUS.equals(currentProcessStatus.getStatus()) || currentProcessStatus.getStatus().equals("Classifying") || currentProcessStatus.getStatus().equals("Promoting")))) {
            promotionService.queueAutomateTaskPromotion(projectKey, taskKey);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Get status of authoring task auto-promotion.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/auto-promote/status")
    public ProcessStatus getAutomateTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
        return promotionService.getAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
    }

    @Operation(summary = "Clear status of authoring task auto-promotion.")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/auto-promote/clear-status")
    public ResponseEntity<String> clearAutomatedTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
        promotionService.clearAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
        return new ResponseEntity<>(HttpStatus.OK);
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

    private List<AuthoringTask> filterJiraTasks(List<AuthoringTask> jiraTasks, List<AuthoringTask> results) {
        if (!jiraTasks.isEmpty()) {
            List<String> authoringTaskKeys = results.stream().map(AuthoringTask::getKey).toList();
            Map<String, AuthoringTask> keyToJiraTask = jiraTasks.stream().collect(
                    Collectors.toMap(AuthoringTask::getKey, Function.identity()));
            for (Map.Entry<String, AuthoringTask> entry : keyToJiraTask.entrySet()) {
                if (!authoringTaskKeys.contains(entry.getKey())) {
                    results.add(entry.getValue());
                }
            }
        }
        return results;
    }

    private List<AuthoringProject> filterJiraProjects(List<AuthoringProject> jiraProjects, List<AuthoringProject> results) {
        if (!jiraProjects.isEmpty()) {
            List<String> authoringProjectKeys = new ArrayList<>(results.stream().map(AuthoringProject::getKey).toList());
            Map<String, AuthoringProject> keyToJiraTask = jiraProjects.stream().collect(
                    Collectors.toMap(AuthoringProject::getKey, Function.identity()));
            for (Map.Entry<String, AuthoringProject> entry : keyToJiraTask.entrySet()) {
                if (!authoringProjectKeys.contains(entry.getKey())) {
                    results.add(entry.getValue());
                    authoringProjectKeys.add(entry.getKey());
                }
            }
        }
        return results;
    }
}
