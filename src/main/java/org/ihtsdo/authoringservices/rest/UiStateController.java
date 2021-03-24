package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.TaskStatus;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.UiStateService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Api("UI State")
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class UiStateController {

	public static final String SHARED = "SHARED";

	@Autowired
	private UiStateService uiStateService;

	@Autowired
	private TaskService taskService;

	@ApiOperation(value = "Persist User UI panel state", notes = "This endpoint may be used to persist UI state using any json object. " +
			"State is stored and retrieved under Project, Task, User and panel. This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}")
	public void persistTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId,
			@RequestBody final String jsonState) throws IOException, BusinessServiceException, JiraException {
		// TODO - move this to an explicit "Start progress" endpoint.
		taskService.conditionalStateTransition(projectKey, taskKey, TaskStatus.NEW, TaskStatus.IN_PROGRESS);
		uiStateService.persistTaskPanelState(projectKey, taskKey, SecurityUtil.getUsername(), panelId, jsonState);
	}

	@ApiOperation(value = "Retrieve User UI panel state", notes = "This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}")
	public String retrieveTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId) throws IOException {
		return uiStateService.retrieveTaskPanelState(projectKey, taskKey, SecurityUtil.getUsername(), panelId);
	}

	@ApiOperation(value = "Delete User UI panel state", notes = "This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@DeleteMapping(value = "/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}")
	public void deleteTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId) throws IOException {
		uiStateService.deleteTaskPanelState(projectKey, taskKey, SecurityUtil.getUsername(), panelId);
	}

	@ApiOperation(value = "Persist Shared UI panel state", notes = "This endpoint may be used to persist UI state using any json object. " +
			"State is stored and retrieved under Project, Task and panel shared between all users. This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}")
	public void persistSharedTaskUiPanelState(
			@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException {
		uiStateService.persistTaskPanelState(projectKey, taskKey, SHARED, panelId, jsonState);
	}

	@ApiOperation(value = "Retrieve Shared UI panel state", notes = "This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}")
	public String retrieveSharedTaskUiPanelState(
			@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId) throws IOException {
		return uiStateService.retrieveTaskPanelState(projectKey, taskKey, SHARED, panelId);
	}

	@ApiOperation(value = "Delete Shared UI panel state", notes = "This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@DeleteMapping(value = "/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}")
	public void deleteSharedTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId) throws IOException {
		uiStateService.deleteTaskPanelState(projectKey, taskKey, SHARED, panelId);
	}


	@ApiOperation(value="Persist User UI panel state", notes="This endpoint may be used to persist UI state using any json object. " +
			"State is stored and retrieved under Project, Task, User and panel. This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@PostMapping(value = "/ui-state/{panelId}")
	public void persistUiPanelState(@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException {
		uiStateService.persistPanelState(SecurityUtil.getUsername(), panelId, jsonState);
	}

	@ApiOperation(value = "Retrieve User UI panel state", notes = "This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@GetMapping(value = "/ui-state/{panelId}")
	public String retrieveUiPanelState(@PathVariable final String panelId) throws IOException {
		return uiStateService.retrievePanelState(SecurityUtil.getUsername(), panelId);
	}

	@ApiOperation(value = "Delete User UI panel state", notes = "This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@DeleteMapping(value = "/ui-state/{panelId}")
	public void deleteUiPanelState(@PathVariable final String panelId) throws IOException {
		uiStateService.deletePanelState(SecurityUtil.getUsername(), panelId);
	}
}
