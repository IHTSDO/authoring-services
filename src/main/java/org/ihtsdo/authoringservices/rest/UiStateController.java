package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.TaskStatus;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.UiStateService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import us.monoid.json.JSONException;

import java.io.IOException;

@Tag(name = "UI State")
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class UiStateController {

	public static final String SHARED = "SHARED";

	@Autowired
	private UiStateService uiStateService;

	@Autowired
	private TaskService taskService;

	@Operation(summary = "Persist User UI panel state",
			description = "This endpoint may be used to persist UI state using any json object. " +
					"State is stored and retrieved under Project, Task, User and panel. " +
					"This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}")
	public void persistTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId,
			@RequestBody final String jsonState) throws IOException, BusinessServiceException, JiraException, JSONException {
		// TODO - move this to an explicit "Start progress" endpoint.
		taskService.conditionalStateTransition(projectKey, taskKey, TaskStatus.NEW, TaskStatus.IN_PROGRESS);
		uiStateService.persistTaskPanelState(projectKey, taskKey, SecurityUtil.getUsername(), panelId, jsonState);
	}

	@Operation(summary = "Retrieve User UI panel state",
			description = "This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}")
	public String retrieveTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId) throws IOException {
		return uiStateService.retrieveTaskPanelState(projectKey, taskKey, SecurityUtil.getUsername(), panelId);
	}

	@Operation(summary = "Delete User UI panel state", description = "This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@DeleteMapping(value = "/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}")
	public void deleteTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId) throws IOException {
		uiStateService.deleteTaskPanelState(projectKey, taskKey, SecurityUtil.getUsername(), panelId);
	}

	@Operation(summary = "Persist Shared UI panel state",
			description = "This endpoint may be used to persist UI state using any json object. " +
					"State is stored and retrieved under Project, Task and panel shared between all users. " +
					"This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}")
	public void persistSharedTaskUiPanelState(
			@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException, JSONException {
		uiStateService.persistTaskPanelState(projectKey, taskKey, SHARED, panelId, jsonState);
	}

	@Operation(summary = "Retrieve Shared UI panel state",
			description = "This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}")
	public String retrieveSharedTaskUiPanelState(
			@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId) throws IOException {
		return uiStateService.retrieveTaskPanelState(projectKey, taskKey, SHARED, panelId);
	}

	@Operation(summary = "Delete Shared UI panel state",
			description = "This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@DeleteMapping(value = "/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}")
	public void deleteSharedTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String panelId) throws IOException {
		uiStateService.deleteTaskPanelState(projectKey, taskKey, SHARED, panelId);
	}

	@Operation(summary = "Persist User UI panel state",
			description = "This endpoint may be used to persist UI state using any json object. " +
					"State is stored and retrieved under Project, Task, User and panel. " +
					"This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@PostMapping(value = "/ui-state/{panelId}")
	public void persistUiPanelState(@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException, JSONException {
		uiStateService.persistPanelState(SecurityUtil.getUsername(), panelId, jsonState);
	}

	@Operation(summary = "Retrieve User UI panel state",
			description = "This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@GetMapping(value = "/ui-state/{panelId}")
	public String retrieveUiPanelState(@PathVariable final String panelId) throws IOException {
		return uiStateService.retrievePanelState(SecurityUtil.getUsername(), panelId);
	}

	@Operation(summary = "Delete User UI panel state",
			description = "This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@DeleteMapping(value = "/ui-state/{panelId}")
	public void deleteUiPanelState(@PathVariable final String panelId) throws IOException {
		uiStateService.deletePanelState(SecurityUtil.getUsername(), panelId);
	}
}
