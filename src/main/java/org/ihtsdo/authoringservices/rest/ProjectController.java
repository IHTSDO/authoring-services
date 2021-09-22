package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.PromotionService;
import org.ihtsdo.authoringservices.service.RebaseService;
import org.ihtsdo.authoringservices.service.ScheduledRebaseService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Api("Authoring Projects")
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

	@ApiOperation(value="List authoring Projects")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects", method= RequestMethod.GET)
	public List<AuthoringProject> listProjects(@RequestParam(value = "lightweight", required = false)  Boolean lightweight) throws JiraException, BusinessServiceException {
		return taskService.listProjects(lightweight);
	}

	@ApiOperation(value="Retrieve an authoring Project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}", method= RequestMethod.GET)
	public AuthoringProject retrieveProject(@PathVariable final String projectKey) throws BusinessServiceException {
		return taskService.retrieveProject(requiredParam(projectKey, PROJECT_KEY));
	}

	@ApiOperation(value="Rebase an authoring Project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/rebase", method= RequestMethod.POST)
	public ResponseEntity<String> rebaseProject(@PathVariable final String projectKey) {
		ProcessStatus processStatus = rebaseService.getProjectRebaseStatus(requiredParam(projectKey, PROJECT_KEY));
		if (processStatus == null || !processStatus.getStatus().equals("Rebasing")) {
			rebaseService.doProjectRebase(projectKey);
		}		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@ApiOperation(value="Get rebase status of an authoring project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/rebase/status", method= RequestMethod.GET)
	public ProcessStatus getProjectRebaseStatus(@PathVariable final String projectKey) {
		return rebaseService.getProjectRebaseStatus(requiredParam(projectKey, PROJECT_KEY));
	}

	@ApiOperation(value="Promote an authoring Project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/promote", method= RequestMethod.POST)
	public ResponseEntity<String> promoteProject(@PathVariable final String projectKey, @RequestBody MergeRequest mergeRequest) {
		ProcessStatus  processStatus = promotionService.getProjectPromotionStatus(requiredParam(projectKey, PROJECT_KEY));
		if (processStatus == null || !processStatus.getStatus().equals("Rebasing")) {
			promotionService.doProjectPromotion(projectKey, mergeRequest);
		}		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@ApiOperation(value="Retrieve status information about project promotion")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/promote/status", method= RequestMethod.GET)
	public ProcessStatus getProjectPromotionStatus(@PathVariable final String projectKey) {
		return promotionService.getProjectPromotionStatus(requiredParam(projectKey, PROJECT_KEY));
	}

	@ApiOperation(value="Retrieve status information about the MAIN branch")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/main", method= RequestMethod.GET)
	public AuthoringMain retrieveMain() throws BusinessServiceException {
		return taskService.retrieveMain();
	}

	@ApiOperation(value="List Tasks within a Project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listTasks(@PathVariable final String projectKey, @RequestParam(value = "lightweight", required = false)  Boolean lightweight) throws BusinessServiceException {
		return taskService.listTasks(requiredParam(projectKey, PROJECT_KEY), lightweight);
	}

	@ApiOperation(value="List authenticated user's Tasks across Projects")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/my-tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listMyTasks(@RequestParam(value = "excludePromoted", required = false) String excludePromoted) throws JiraException, BusinessServiceException {
		return taskService.listMyTasks(SecurityUtil.getUsername(), excludePromoted);
	}

	@ApiOperation(value="List review tasks, with the current user or unassigned reviewer, across Projects")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/review-tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listMyOrUnassignedReviewTasks(@RequestParam(value = "excludePromoted", required = false) String excludePromoted) throws JiraException, BusinessServiceException {
		return taskService.listMyOrUnassignedReviewTasks(excludePromoted);
	}

	@ApiOperation(value = "Search tasks across Projects")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value = "/projects/tasks/search", method = RequestMethod.GET)
	public List<AuthoringTask> searchTasks(@RequestParam(value = "criteria") String criteria, @RequestParam(value = "lightweight", required = false) Boolean lightweight) throws JiraException, BusinessServiceException {
		return taskService.searchTasks(criteria, lightweight);
	}

	@ApiOperation(value="Retrieve a Task within a Project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}", method= RequestMethod.GET)
	public AuthoringTask retrieveTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return taskService.retrieveTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), false);
	}

	@ApiOperation(value="Create a Task within a Project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.POST)
	public AuthoringTask createTask(@PathVariable final String projectKey, @RequestBody final AuthoringTaskCreateRequest taskCreateRequest) throws BusinessServiceException {
		return taskService.createTask(requiredParam(projectKey, PROJECT_KEY), taskCreateRequest);
	}

	@ApiOperation(value = "Update a Task")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}", method = RequestMethod.PUT)
	public AuthoringTask updateTask(@PathVariable final String projectKey, @PathVariable final String taskKey,  @RequestBody final AuthoringTaskUpdateRequest updatedTask) throws BusinessServiceException {
		return taskService.updateTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), updatedTask);
	}
	
	@ApiOperation(value = "Update a Project")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value = "/projects/{projectKey}", method = RequestMethod.PUT)
	public AuthoringProject updateProject(@PathVariable final String projectKey,  @RequestBody final AuthoringProject updatedProject) throws BusinessServiceException {
		return taskService.updateProject(requiredParam(projectKey, PROJECT_KEY),  updatedProject);
	}

	@ApiOperation(value = "Retrieve Task Attachments")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/attachments", method = RequestMethod.GET)
	public List<TaskAttachment> getAttachmentsForTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return taskService.getTaskAttachments(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}

	@ApiOperation(value = "Leave comment for Task")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/comment", method = RequestMethod.POST)
	public void leaveComment(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestBody final String comment) throws BusinessServiceException {
		taskService.leaveCommentForTask(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), comment);
	}

	@ApiOperation(value="Rebase an authoring Task")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase", method= RequestMethod.POST)
	public ResponseEntity<String> rebaseTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		ProcessStatus  processStatus = rebaseService.getTaskRebaseStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		if (processStatus == null || !processStatus.getStatus().equals("Rebasing")) {
			rebaseService.doTaskRebase(projectKey, taskKey);
		}		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@ApiOperation(value="Get rebase status of an authoring Task")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase/status", method= RequestMethod.GET)
	public ProcessStatus getRebaseTaskStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		return rebaseService.getTaskRebaseStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}

	@ApiOperation(value="Promote an authoring Task")
	@ApiResponse(code = 200, message = "OK")
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
	
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/promote/status", method= RequestMethod.GET)
	public ProcessStatus getTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		return promotionService.getTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}
	
	@ApiOperation(value="Auto promote an authoring Task")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote", method= RequestMethod.POST)
	public ResponseEntity<String> autoPromoteTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		ProcessStatus currentProcessStatus = promotionService.getAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		if (!(null != currentProcessStatus && (currentProcessStatus.getStatus().equals("Rebasing") || currentProcessStatus.getStatus().equals("Classifying") || currentProcessStatus.getStatus().equals("Promoting")))) {
			promotionService.queueAutomateTaskPromotion(projectKey, taskKey);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@ApiOperation(value="Get status of authoring task auto-promotion.")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote/status", method= RequestMethod.GET)
	public ProcessStatus getAutomateTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		return promotionService.getAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}

	@ApiOperation(value="Clear status of authoring task auto-promotion.")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote/clear-status", method= RequestMethod.POST)
	public ResponseEntity<String> clearAutomatedTaskPromotionStatus(@PathVariable final String projectKey, @PathVariable final String taskKey) {
		promotionService.clearAutomateTaskPromotionStatus(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@ApiOperation(
			value="Manual trigger for scheduled project rebase process.",
			notes = "This endpoint is asynchronous so will return straight away.")
	@ApiResponse(code = 200, message = "OK")
	@RequestMapping(value="/projects/auto-rebase", method= RequestMethod.POST)
	public ResponseEntity<String> autoRebaseProjects() throws BusinessServiceException {
		scheduledRebaseService.rebaseProjectsManualTrigger();
		return new ResponseEntity<>(HttpStatus.OK);
	}

}
