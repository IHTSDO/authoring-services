/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api.admin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.exception.admin.LockConflictException;
import com.b2international.snowowl.rest.exception.admin.LockException;
import com.b2international.snowowl.rest.service.admin.IRepositoryService;
import com.mangofactory.swagger.annotations.ApiIgnore;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * Spring controller for exposing {@link IRepositoryService} functionality.
 * 
 * @author apeteri
 */
@RestController
@RequestMapping(value={"/repositories"}, produces={ MediaType.TEXT_PLAIN_VALUE })
@Api("Administration")
@ApiIgnore
public class RepositoryRestService extends AbstractAdminRestService {

	@Autowired
	protected IRepositoryService delegate;

	@ExceptionHandler(LockConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public @ResponseBody String handleLockConflictException(final LockConflictException e) {
		return handleException(e);
	}

	@ExceptionHandler(LockException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public @ResponseBody String handleLockException(final LockException e) {
		return handleException(e);
	}

	@RequestMapping(method=RequestMethod.GET)
	@ApiOperation(
			value="Retrieve all repository identifiers",
			notes="Retrieves the unique identifier of each running repository that stores terminology content.")
	public String getRepositoryUuids() {
		final List<String> repositoryUuids = delegate.getRepositoryUuids();
		return joinStrings(repositoryUuids);
	}

	@RequestMapping(value="lock", method=RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiOperation(
			value="Lock all repositories",
			notes="Places a global lock, which prevents other users from making changes to any of the repositories "
					+ "while a backup is created. The call may block up to the specified timeout to acquire the lock; "
					+ "if timeoutMillis is set to 0, it returns immediately.")
	@ApiResponses({
		@ApiResponse(code=204, message="Lock successful"),
		@ApiResponse(code=409, message="Conflicting lock already taken"),
		@ApiResponse(code=400, message="Illegal timeout value, or locking-related issue")
	})
	public void lockGlobal(
			@RequestParam(value="timeoutMillis", defaultValue="5000", required=false) 
			@ApiParam(value="lock timeout in milliseconds")
			final int timeoutMillis) {

		delegate.lockGlobal(timeoutMillis);
	}

	@RequestMapping(value="unlock", method=RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiOperation(
			value="Unlock all repositories",
			notes="Releases a previously acquired global lock.")
	@ApiResponses({
		@ApiResponse(code=204, message="Unlock successful"),
		@ApiResponse(code=400, message="Unspecified unlock-related issue")
	})
	public void unlockGlobal() {
		delegate.unlockGlobal();
	}

	@RequestMapping(value="{repositoryUuid}/lock", method=RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiOperation(
			value="Lock single repository",
			notes="Places a repository-level lock, which prevents other users from making changes to the specified repository. "
					+ "The call may block up to the specified timeout to acquire the lock; if timeoutMillis is set to 0, "
					+ "it returns immediately.")
	@ApiResponses({
		@ApiResponse(code=204, message="Lock successful"),
		@ApiResponse(code=409, message="Conflicting lock already taken"),
		@ApiResponse(code=404, message="Repository not found"),
		@ApiResponse(code=400, message="Illegal timeout value, or locking-related issue")
	})
	public void lockRepository(
			@PathVariable(value="repositoryUuid") 
			@ApiParam(value="a unique identifier pointing to a particular repository")
			final String repositoryUuid, 

			@RequestParam(value="timeoutMillis", defaultValue="5000", required=false)
			@ApiParam(value="lock timeout in milliseconds")
			final int timeoutMillis) {

		delegate.lockRepository(repositoryUuid, timeoutMillis);
	}

	@RequestMapping(value="{repositoryUuid}/unlock", method=RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiOperation(
			value="Unlock single repository",
			notes="Releases a previously acquired repository-level lock on the specified repository.")
	@ApiResponses({
		@ApiResponse(code=204, message="Unlock successful"),
		@ApiResponse(code=404, message="Repository not found"),
		@ApiResponse(code=400, message="Unspecified unlock-related issue")
	})
	public void unlockRepository(
			@PathVariable(value="repositoryUuid") 
			@ApiParam(value="a unique identifier pointing to a particular repository")
			final String repositoryUuid) {

		delegate.unlockRepository(repositoryUuid);
	}

	@RequestMapping(value="{repositoryUuid}/versions", method=RequestMethod.GET)
	@ApiOperation(
			value="Retrieve all version identifiers for a repository",
			notes="Retrieves all version identifiers for the specified repository.")
	@ApiResponses({
		@ApiResponse(code=404, message="Repository not found")
	})
	public String getRepositoryVersionIds(
			@PathVariable(value="repositoryUuid") 
			@ApiParam(value="a unique identifier pointing to a particular repository")
			final String repositoryUuid) {

		final List<String> versions = delegate.getRepositoryVersionIds(repositoryUuid);
		return joinStrings(versions);
	}

	@RequestMapping(value="{repositoryUuid}/versions/{repositoryVersionId}/indexFiles", method=RequestMethod.GET)
	@ApiOperation(
			value="Retrieve all repository version index file paths",
			notes="Retrieves the relative path of all files that make up the index of the specified repository and version.")
	@ApiResponses({
		@ApiResponse(code=404, message="Repository or version not found"),
	})
	public String getRepositoryVersionIndexFiles(
			@PathVariable(value="repositoryUuid") 
			@ApiParam(value="a unique identifier pointing to a particular repository")
			final String repositoryUuid,

			@PathVariable(value="repositoryVersionId") 
			@ApiParam(value="the identifier of a repository version")
			final String repositoryVersionId) {

		final List<String> fileList = delegate.getRepositoryVersionIndexFiles(repositoryUuid, repositoryVersionId);
		return joinStrings(fileList);
	}
}
