package org.ihtsdo.snowowl.authoring.single.api.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.snowowl.authoring.single.api.service.*;
import us.monoid.json.JSONException;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.snowowl.PathHelper;
import org.ihtsdo.otf.rest.client.snowowl.pojo.ApiError;
import org.ihtsdo.otf.rest.client.snowowl.pojo.Merge;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ProjectController {

	@Autowired
	private TaskService taskService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private NotificationService notificationService;

	@ApiOperation(value="List authoring Projects")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects", method= RequestMethod.GET)
	public List<AuthoringProject> listProjects() throws JiraException, BusinessServiceException {
		return taskService.listProjects();
	}

	@ApiOperation(value="Retrieve an authoring Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}", method= RequestMethod.GET)
	public AuthoringProject retrieveProject(@PathVariable final String projectKey) throws BusinessServiceException {
		return taskService.retrieveProject(projectKey);
	}

	@ApiOperation(value="Rebase an authoring Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/rebase", method= RequestMethod.POST)
	public ResponseEntity<String> rebaseProject(@PathVariable final String projectKey) throws BusinessServiceException {
		String projectBranchPath = taskService.getProjectBranchPathUsingCache(projectKey);
		Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(projectBranchPath), projectBranchPath, null);
		return getResponseEntity(merge);
	}

	@ApiOperation(value="Promote an authoring Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/promote", method= RequestMethod.POST)
	public ResponseEntity<String> promoteProject(@PathVariable final String projectKey, @RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		String projectBranchPath = taskService.getProjectBranchPathUsingCache(projectKey);
		Merge merge = branchService.mergeBranchSync(projectBranchPath, PathHelper.getParentPath(projectBranchPath), mergeRequest.getSourceReviewId());
		if (merge.getStatus() == Merge.Status.COMPLETED) {
			List<Issue> promotedIssues = taskService.getTaskIssues(projectKey, TaskStatus.PROMOTED);
			taskService.stateTransition(promotedIssues, TaskStatus.COMPLETED);
			notificationService.queueNotification(ControllerHelper.getUsername(),
					new Notification(projectKey, null, EntityType.Promotion, "Project successfully promoted"));
		}
		return getResponseEntity(merge);
	}

	@ApiOperation(value="Retrieve status information about the MAIN branch")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/main", method= RequestMethod.GET)
	public AuthoringMain retrieveMain() throws BusinessServiceException {
		return taskService.retrieveMain();
	}

	@ApiOperation(value="List Tasks within a Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listTasks(@PathVariable final String projectKey) throws BusinessServiceException {
		return taskService.listTasks(projectKey);
	}

	@ApiOperation(value="List authenticated user's Tasks across Projects")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/my-tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listMyTasks(@RequestParam(value = "excludePromoted", required = false) String excludePromoted) throws JiraException, BusinessServiceException {
		return taskService.listMyTasks(ControllerHelper.getUsername(), excludePromoted);
	}

	@ApiOperation(value="List review tasks, with the current user or unassigned reviewer, across Projects")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/review-tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listMyOrUnassignedReviewTasks() throws JiraException, BusinessServiceException {
		return taskService.listMyOrUnassignedReviewTasks();
	}

	@ApiOperation(value="Retrieve a Task within a Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}", method= RequestMethod.GET)
	public AuthoringTask retrieveTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return taskService.retrieveTask(projectKey, taskKey);
	}

	@ApiOperation(value="Create a Task within a Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.POST)
	public AuthoringTask createTask(@PathVariable final String projectKey, @RequestBody final AuthoringTaskCreateRequest taskCreateRequest) throws BusinessServiceException {
		return taskService.createTask(projectKey, taskCreateRequest);
	}

	@ApiOperation(value = "Update a Task")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}", method = RequestMethod.PUT)
	public AuthoringTask updateTask(@PathVariable final String projectKey, @PathVariable final String taskKey,  @RequestBody final AuthoringTaskUpdateRequest updatedTask) throws BusinessServiceException {
		return taskService.updateTask(projectKey, taskKey, updatedTask);
	}

	@ApiOperation(value = "Retrieve Task Attachments")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/attachments", method = RequestMethod.GET)
	public List<TaskAttachment> getAttachmentsForTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return taskService.getTaskAttachments(projectKey, taskKey);
	}

	@ApiOperation(value = "Leave comment for Task")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/comment", method = RequestMethod.POST)
	public void leaveComment(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestBody final String comment) throws BusinessServiceException {
		taskService.leaveCommentForTask(projectKey, taskKey, comment);
	}

	@ApiOperation(value="Rebase an authoring Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase", method= RequestMethod.POST)
	public ResponseEntity<String> rebaseTask(@PathVariable final String projectKey,
											 @PathVariable final String taskKey) throws BusinessServiceException {
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(taskBranchPath), taskBranchPath, null);
		return getResponseEntity(merge);
	}

	@ApiOperation(value="Promote an authoring Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/promote", method= RequestMethod.POST)
	public ResponseEntity<String> promoteTask(@PathVariable final String projectKey,
											  @PathVariable final String taskKey,
											  @RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		Merge merge = branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), mergeRequest.getSourceReviewId());
		if (merge.getStatus() == Merge.Status.COMPLETED) {
			taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
			notificationService.queueNotification(ControllerHelper.getUsername(),
					new Notification(projectKey, taskKey, EntityType.Promotion, "Task successfully promoted"));
		}
		return getResponseEntity(merge);
	}
	
	@ApiOperation(value="Auto promote an authoring Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote", method= RequestMethod.POST)
	public ResponseEntity<String> autoPromoteTask(@PathVariable final String projectKey,
											  @PathVariable final String taskKey) throws BusinessServiceException {
		ProcessStatus currentProcessStatus = taskService.getAutoPromoteStatus(projectKey, taskKey);
		if (!(null != currentProcessStatus && (currentProcessStatus.getStatus().equals("Rebasing") || currentProcessStatus.getStatus().equals("Classifying") || currentProcessStatus.getStatus().equals("Promoting")))) {
			taskService.autoPromoteTaskToProject(projectKey, taskKey);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@ApiOperation(value="Get status of authoring task auto-promotion.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/auto-promote/status", method= RequestMethod.GET)
	public ProcessStatus getAutoPromoteTaskStatus(@PathVariable final String projectKey,
										   @PathVariable final String taskKey) throws BusinessServiceException {
		return taskService.getAutoPromoteStatus(projectKey, taskKey);
	}

	private ResponseEntity<String> getResponseEntity(Merge merge) {
		if (merge.getStatus() == Merge.Status.COMPLETED) {
			return new ResponseEntity<>(HttpStatus.OK);
		} else {
			ApiError apiError = merge.getApiError();
			String message = apiError != null ? apiError.getMessage() : null;
			return new ResponseEntity<>(message, HttpStatus.CONFLICT);
		}
	}

}
