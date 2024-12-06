package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.service.CodeSystemService;
import org.ihtsdo.authoringservices.service.DailyBuildService;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Tag(name = "Code Systems")
@RestController
@RequestMapping(value = "/codesystems", produces = {MediaType.APPLICATION_JSON_VALUE})
public class CodeSystemController {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private DailyBuildService dailyBuildService;

	@Operation(summary = "List code systems")
	@GetMapping
	public List <AuthoringCodeSystem> listCodeSystems() throws BusinessServiceException {
		return codeSystemService.findAll();
	}

	@Operation(summary = "Upgrade code system to a different dependant version asynchronously")
	@ApiResponse(responseCode = "201", description = "CREATED")
	@PostMapping(value="/{shortName}/upgrade/{newDependantVersion}")
	public ResponseEntity<Void> upgradeCodeSystem(
			@Parameter(description = "Extension code system shortname") @PathVariable final String shortName,
			@Parameter(description = "New dependant version with the same format as the effectiveTime RF2 field, for example '20190731'") @PathVariable final Integer newDependantVersion,
			@Parameter(description = "Flag to generate additional english language refset") @RequestParam(required = false) final Boolean generateEn_GbLanguageRefsetDelta,
			@Parameter(description = "Master Project Key which is required by generating the additional english language refset process") @RequestParam(required = false) final String projectKey) throws BusinessServiceException, ServiceException {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();

		String jobId = codeSystemService.upgrade(shortName, newDependantVersion);
		codeSystemService.waitForCodeSystemUpgradeToComplete(jobId, generateEn_GbLanguageRefsetDelta, projectKey, SecurityContextHolder.getContext());

		String requestUrl = request.getRequestURL().toString();
		requestUrl = requestUrl.replace("/" + shortName, "").replace("/" + newDependantVersion, "");
		return new ResponseEntity<>(ControllerHelper.getCreatedLocationHeaders(requestUrl + "/", jobId, null), HttpStatus.CREATED);
	}


	@Operation(summary = "Retrieve an upgrade job", description = "Retrieves the state of an upgrade job. Used to view the upgrade configuration and check its status.")
	@GetMapping(value = "/upgrade/{jobId}")
	public CodeSystemUpgradeJob getUpgradeJob(@PathVariable String jobId) throws RestClientException {
		return codeSystemService.getUpgradeJob(jobId);
	}

	@Operation(summary = "Download daily build package for a given code system")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value = "/{shortName}/daily-build-package/download")
	public void downloadDailyBuildPackageFile(@PathVariable final String shortName, final HttpServletResponse response) throws IOException {
		String latestDailyBuildFileName = dailyBuildService.getLatestDailyBuildFileName(shortName);
		if (latestDailyBuildFileName != null) {
			try (InputStream outputFileStream = dailyBuildService.downloadDailyBuildPackage(shortName, latestDailyBuildFileName)) {
				if (outputFileStream == null) {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				} else {
					response.setHeader("Content-Disposition", "attachment; filename=\"" + latestDailyBuildFileName + "\"" );
					response.setContentType("text/plain; charset=utf-8");
					StreamUtils.copy(outputFileStream, response.getOutputStream());
				}
			}
		} else {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	@Operation(summary = "Lock all projects for a given code system")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value = "/{shortName}/projects/lock")
	public void lockProjects(@PathVariable final String shortName, @RequestParam(value = "useNew", required = false) Boolean useNew, final HttpServletResponse response) throws BusinessServiceException {
		codeSystemService.lockProjects(shortName, useNew);
	}

	@Operation(summary = "Unlock all projects for a given code system")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value = "/{shortName}/projects/unlock")
	public void unlockProjects(@PathVariable final String shortName, @RequestParam(value = "useNew", required = false) Boolean useNew, final HttpServletResponse response) throws BusinessServiceException {
		codeSystemService.unlockProjects(shortName, useNew);
	}
}
