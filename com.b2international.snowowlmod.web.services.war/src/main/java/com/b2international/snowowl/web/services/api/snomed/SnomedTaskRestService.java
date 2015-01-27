/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api.snomed;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.net.URI;
import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.domain.task.ITask;
import com.b2international.snowowl.rest.domain.task.TaskState;
import com.b2international.snowowl.rest.exception.BadRequestException;
import com.b2international.snowowl.rest.impl.domain.task.TaskChangeRequest;
import com.b2international.snowowl.rest.impl.domain.task.TaskInput;
import com.b2international.snowowl.rest.snomed.service.ISnomedTaskService;
import com.b2international.snowowl.web.services.api.AbstractRestService;
import com.b2international.snowowl.web.services.api.AbstractTaskRestService;
import com.b2international.snowowl.web.services.domain.CollectionResource;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * @author apeteri
 * @since 1.0
 * @since 1.0
 */
@Api("SNOMED CT Tasks")
@RestController
@RequestMapping(
		value="/snomed-ct/{version}/tasks", 
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class SnomedTaskRestService extends AbstractTaskRestService {

	private static final String SHORT_NAME = "SNOMEDCT";

	@Autowired
	public void setTaskService(final ISnomedTaskService delegate) {
		this.delegate = delegate;
	}

	@ApiOperation(
			value="Retrieve all tasks of a version",
			notes="Returns a list containing all tasks of the specified version.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(method=RequestMethod.GET)
	public CollectionResource<ITask> getTasks(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="Include promoted tasks")
			@RequestParam(value="includePromoted", defaultValue="false", required=false) 
			final boolean includePromoted) {

		return super.getTasks(SHORT_NAME, version, includePromoted);
	}

	@ApiOperation(
			value="Create a task on a version",
			notes="Creates a new task using the specified version and task identifier, if it does not exist.")
	@ApiResponses({
		@ApiResponse(code = 201, message = "Created"),
		@ApiResponse(code = 404, message = "Code system version not found"),
		@ApiResponse(code = 409, message = "Conflict on already existing task identifier")
	})
	@RequestMapping(
			method=RequestMethod.POST,
			consumes={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<Void> createTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="Task parameters")
			@RequestBody 
			final TaskInput taskInput,
			
			final Principal principal) {
		return super.createTask(SHORT_NAME, version, taskInput, principal);
	}

	@ApiOperation(
			value="Retrieve task of a version by identifier",
			notes="Returns a single task with the specified version and task identifier, if it exists.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version or task not found")
	})
	@RequestMapping(value="/{taskId}", method=RequestMethod.GET)
	public ITask getTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId) {

		return super.getTaskByName(SHORT_NAME, version, taskId);
	}

	@ApiOperation(
			value="Promote and/or Synchronize task",
			notes="Promotion pushes all changes that happened on the task branch to the parent version branch.<p>"
					+ "Synchronization pulls all changes that have happened on the parent version branch.")
	@ApiResponses({
		@ApiResponse(code = 204, message = "No content, promote/synchronize successful"),
		@ApiResponse(code = 404, message = "Code system version or task not found")
	})
	@RequestMapping(value="/{taskId}", method=RequestMethod.PUT)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId,

			@ApiParam(value="Task parameters")
			@RequestBody 
			final TaskChangeRequest change,

			final Principal principal) {

		if (change.getState() == TaskState.NOT_SYNCHRONIZED) {
			throw new BadRequestException("Cannot modify state of task %s to %s", taskId, TaskState.NOT_SYNCHRONIZED);
		}
		final ITask taskByName = getTask(version, taskId);
		// sync if the request wants sync or promote
		if (taskByName.getState() == TaskState.NOT_SYNCHRONIZED && (change.getState() == TaskState.SYNCHRONIZED || change.getState() == TaskState.PROMOTED)) {
			synchronizeTask(SHORT_NAME, version, taskId, principal);
		}
		// promote if wants to be promoted, include sync in promoted if it was unsync
		if (taskByName.getState() != TaskState.PROMOTED && change.getState() == TaskState.PROMOTED) {
			promoteTask(SHORT_NAME, version, taskId, principal);
		}
	}
	
	@Override
	protected URI createTaskLocationUri(final String shortName, final String version, final String taskId) {
		return linkTo(methodOn(SnomedTaskRestService.class).getTask(version, taskId)).toUri();
	}
}
