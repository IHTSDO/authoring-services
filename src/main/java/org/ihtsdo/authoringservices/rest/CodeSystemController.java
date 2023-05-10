package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.*;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.service.CodeSystemService;
import org.ihtsdo.authoringservices.service.DailyBuildService;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Api("Code Systems")
@RestController
@RequestMapping(value = "/codesystems", produces = {MediaType.APPLICATION_JSON_VALUE})
public class CodeSystemController {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private DailyBuildService dailyBuildService;

	@ApiOperation(value = "List code systems")
	@GetMapping
	public List <AuthoringCodeSystem> listCodeSystems() throws BusinessServiceException {
		return codeSystemService.findAll();
	}

	@ApiOperation(value="Upgrade code system to a different dependant version asynchronously")
	@ApiResponse(code = 201, message = "CREATED")
	@RequestMapping(value="/{shortName}/upgrade/{newDependantVersion}", method= RequestMethod.POST)
	public ResponseEntity<Void> upgradeCodeSystem(@ApiParam(value="Extension code system shortname") @PathVariable final String shortName,
												  @ApiParam(value="New dependant version with the same format as the effectiveTime RF2 field, for example '20190731'") @PathVariable final Integer newDependantVersion,
												  @ApiParam(value="Flag to generate additional english language refset") @RequestParam(required = false) final Boolean generateEn_GbLanguageRefsetDelta,
												  @ApiParam(value="Master Project Key which is required by generating the additional english language refset process") @RequestParam(required = false) final String projectKey) throws BusinessServiceException {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();

		String jobId = codeSystemService.upgrade(shortName, newDependantVersion);
		codeSystemService.waitForCodeSystemUpgradeToComplete(jobId, generateEn_GbLanguageRefsetDelta, projectKey, SecurityContextHolder.getContext());

		String requestUrl = request.getRequestURL().toString();
		requestUrl = requestUrl.replace("/" + shortName, "").replace("/" + newDependantVersion, "");
		return new ResponseEntity<>(ControllerHelper.getCreatedLocationHeaders(requestUrl + "/", jobId, null), HttpStatus.CREATED);
	}


	@ApiOperation(value = "Retrieve an upgrade job.",
			notes = "Retrieves the state of an upgrade job. Used to view the upgrade configuration and check its status.")
	@GetMapping(value = "/upgrade/{jobId}")
	public CodeSystemUpgradeJob getUpgradeJob(@PathVariable String jobId) throws RestClientException {
		return codeSystemService.getUpgradeJob(jobId);
	}

	@ApiOperation(value = "Download daily build package for a given code system")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
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

	@ApiOperation(value = "Lock all projects for a given code system")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@PostMapping(value = "/{shortName}/projects/lock")
	public void lockProjects(@PathVariable final String shortName, final HttpServletResponse response) throws BusinessServiceException, JiraException {
		codeSystemService.lockProjects(shortName);
	}

	@ApiOperation(value = "Unlock all projects for a given code system")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@PostMapping(value = "/{shortName}/projects/unlock")
	public void unlockProjects(@PathVariable final String shortName, final HttpServletResponse response) throws BusinessServiceException, JiraException {
		codeSystemService.unlockProjects(shortName);
	}
}
