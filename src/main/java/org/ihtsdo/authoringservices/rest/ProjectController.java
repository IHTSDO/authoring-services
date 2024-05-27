package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.PromotionService;
import org.ihtsdo.authoringservices.service.RebaseService;
import org.ihtsdo.authoringservices.service.ScheduledRebaseService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Tag(name = "Authoring Projects")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ProjectController {

	@Autowired
	private TaskService taskService;

	@Autowired
	private PromotionService promotionService;
	
	@Autowired
	private RebaseService rebaseService;

	@Autowired
	private ScheduledRebaseService scheduledRebaseService;

	@Operation(summary = "Retrieve Authoring Info (Code System, Project and Task information) for a given branch")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/branches/{branch}/authoring-info", method= RequestMethod.GET)
	public ResponseEntity<AuthoringInformation> getBranchAuthoringInformation(@PathVariable String branch) throws BusinessServiceException, ServiceException, RestClientException {
		String branchPath = BranchPathUriUtil.decodePath(branch);
		return new ResponseEntity<>(taskService.getBranchAuthoringInformation(branchPath), HttpStatus.OK);
	}

	@Operation(summary = "List authoring projects")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects", method= RequestMethod.GET)
	public List<AuthoringProject> listProjects(@RequestParam(value = "lightweight", required = false)  Boolean lightweight) throws JiraException, BusinessServiceException {
		return taskService.listProjects(lightweight);
	}

	@Operation(summary = "Retrieve an authoring project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}", method= RequestMethod.GET)
	public AuthoringProject retrieveProject(@PathVariable final String projectKey) throws BusinessServiceException {
		return taskService.retrieveProject(requiredParam(projectKey, PROJECT_KEY));
	}

	@Operation(summary = "Rebase an authoring project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/rebase", method= RequestMethod.POST)
	public ResponseEntity<String> rebaseProject(@PathVariable final String projectKey) throws BusinessServiceException {
		ProcessStatus processStatus = rebaseService.getProjectRebaseStatus(requiredParam(projectKey, PROJECT_KEY));
		if (processStatus == null || !processStatus.getStatus().equals("Rebasing")) {
			rebaseService.doProjectRebase(projectKey);
		}		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@Operation(summary = "Get rebase status of an authoring project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/rebase/status", method= RequestMethod.GET)
	public ProcessStatus getProjectRebaseStatus(@PathVariable final String projectKey) {
		return rebaseService.getProjectRebaseStatus(requiredParam(projectKey, PROJECT_KEY));
	}

	@Operation(summary = "Promote an authoring project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/promote", method= RequestMethod.POST)
	public ResponseEntity<String> promoteProject(@PathVariable final String projectKey, @RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		ProcessStatus  processStatus = promotionService.getProjectPromotionStatus(requiredParam(projectKey, PROJECT_KEY));
		if (processStatus == null || !processStatus.getStatus().equals("Rebasing")) {
			promotionService.doProjectPromotion(projectKey, mergeRequest);
		}		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@Operation(summary = "Retrieve status information about project promotion")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/promote/status", method= RequestMethod.GET)
	public ProcessStatus getProjectPromotionStatus(@PathVariable final String projectKey) {
		return promotionService.getProjectPromotionStatus(requiredParam(projectKey, PROJECT_KEY));
	}

	@Operation(summary = "Retrieve status information about the MAIN branch")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/main", method= RequestMethod.GET)
	public AuthoringMain retrieveMain() throws BusinessServiceException {
		return taskService.retrieveMain();
	}

	@Operation(summary = "List tasks within a project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listTasks(@PathVariable final String projectKey, @RequestParam(value = "lightweight", required = false)  Boolean lightweight) throws BusinessServiceException {
		return taskService.listTasks(requiredParam(projectKey, PROJECT_KEY), lightweight);
	}

	@Operation(summary = "List authenticated user's tasks across projects")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/my-tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listMyTasks(@RequestParam(value = "excludePromoted", required = false) String excludePromoted) throws JiraException, BusinessServiceException {
		return taskService.listMyTasks(SecurityUtil.getUsername(), excludePromoted);
	}

	@Operation(summary = "List review tasks, with the current user or unassigned reviewer, across projects")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/review-tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listMyOrUnassignedReviewTasks(@RequestParam(value = "excludePromoted", required = false) String excludePromoted) throws JiraException, BusinessServiceException {
		return taskService.listMyOrUnassignedReviewTasks(excludePromoted);
	}

	@Operation(summary = "Search tasks across projects")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value = "/projects/tasks/search", method = RequestMethod.GET)
	public List<AuthoringTask> searchTasks(@RequestParam(value = "criteria") String criteria, @RequestParam(value = "lightweight", required = false) Boolean lightweight) throws JiraException, BusinessServiceException {
		return taskService.searchTasks(criteria, lightweight);
	}

	@Operation(summary = "Retrieve a task within a project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}", method= RequestMethod.GET)
	public AuthoringTask retrieveTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return taskService.retrieveTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), false);
	}

	@Operation(summary = "Create a task within a project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.POST)
	public AuthoringTask createTask(@PathVariable final String projectKey, @RequestBody final AuthoringTaskCreateRequest taskCreateRequest) throws BusinessServiceException {
		return taskService.createTask(requiredParam(projectKey, PROJECT_KEY), taskCreateRequest);
	}

	@Operation(summary = "Update a task")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}", method = RequestMethod.PUT)
	public AuthoringTask updateTask(@PathVariable final String projectKey, @PathVariable final String taskKey,  @RequestBody final AuthoringTaskUpdateRequest updatedTask) throws BusinessServiceException {
		return taskService.updateTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), updatedTask);
	}
	
	@Operation(summary = "Update a project")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value = "/projects/{projectKey}", method = RequestMethod.PUT)
	public AuthoringProject updateProject(@PathVariable final String projectKey,  @RequestBody final AuthoringProject updatedProject) throws BusinessServiceException {
		return taskService.updateProject(requiredParam(projectKey, PROJECT_KEY),  updatedProject);
	}

	@Operation(summary = "Retrieve task attachments")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/attachments", method = RequestMethod.GET)
	public List<TaskAttachment> getAttachmentsForTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return taskService.getTaskAttachments(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}

	@Operation(summary = "Leave comment for a task")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/comment", method = RequestMethod.POST)
	public void leaveComment(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestBody final String comment) throws BusinessServiceException {
		taskService.leaveCommentForTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), comment);
	}

	@Operation(summary = "Rebase an authoring task")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase", method= RequestMethod.POST)
	public ResponseEntity<String> rebaseTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		ProcessStatus  processStatus = rebaseService.getTaskRebaseStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		if (processStatus == null || !processStatus.getStatus().equals("Rebasing")) {
			rebaseService.doTaskRebase(projectKey, taskKey);
		}		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@Operation(summary = "Get rebase status of an authoring task")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase/status", method= RequestMethod.GET)
	public ProcessStatus getRebaseTaskStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		return rebaseService.getTaskRebaseStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}

	@Operation(summary = "Promote an authoring task")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/promote", method= RequestMethod.POST)
	public ResponseEntity<String> promoteTask(@PathVariable final String projectKey,
											  @PathVariable final String taskKey,
											  @RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		ProcessStatus  processStatus = promotionService.getTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		if (processStatus == null || !processStatus.getStatus().equals("Rebasing")) {
			promotionService.doTaskPromotion(projectKey, taskKey, mergeRequest);
		}		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(summary = "Get status of authoring task promotion.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/promote/status", method= RequestMethod.GET)
	public ProcessStatus getTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		return promotionService.getTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}
	
	@Operation(summary = "Auto-promote an authoring task")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote", method= RequestMethod.POST)
	public ResponseEntity<String> autoPromoteTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		ProcessStatus currentProcessStatus = promotionService.getAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		if (!(null != currentProcessStatus && (currentProcessStatus.getStatus().equals("Rebasing") || currentProcessStatus.getStatus().equals("Classifying") || currentProcessStatus.getStatus().equals("Promoting")))) {
			promotionService.queueAutomateTaskPromotion(projectKey, taskKey);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@Operation(summary = "Get status of authoring task auto-promotion.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote/status", method= RequestMethod.GET)
	public ProcessStatus getAutomateTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		return promotionService.getAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}

	@Operation(summary = "Clear status of authoring task auto-promotion.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote/clear-status", method= RequestMethod.POST)
	public ResponseEntity<String> clearAutomatedTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		promotionService.clearAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(
			summary = "Manual trigger for scheduled project rebase process.",
			description = "This endpoint is asynchronous so will return straight away.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/auto-rebase", method= RequestMethod.POST)
	public ResponseEntity<String> autoRebaseProjects() throws BusinessServiceException {
		scheduledRebaseService.rebaseProjectsManualTrigger();
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(summary = "Lock project")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value = "/projects/{projectKey}/lock")
	public void lockProjects(@PathVariable final String projectKey) throws BusinessServiceException {
		taskService.lockProject(projectKey);
	}

	@Operation(summary = "Unlock project")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value = "/projects/{projectKey}/unlock")
	public void unlockProjects(@PathVariable final String projectKey) throws BusinessServiceException {
		taskService.unlockProject(projectKey);
	}

}
