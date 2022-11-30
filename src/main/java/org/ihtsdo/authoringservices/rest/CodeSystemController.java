package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import org.ihtsdo.authoringservices.service.CodeSystemUpgradeService;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Api("Code Systems")
@RestController
@RequestMapping(value = "/codesystems", produces = {MediaType.APPLICATION_JSON_VALUE})
public class CodeSystemController {

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;

	@ApiOperation(value="Upgrade code system to a different dependant version asynchronously")
	@ApiResponse(code = 201, message = "CREATED")
	@RequestMapping(value="/{shortName}/upgrade/{newDependantVersion}", method= RequestMethod.POST)
	public ResponseEntity<Void> upgradeCodeSystem(@ApiParam(value="Extension code system shortname") @PathVariable final String shortName,
												  @ApiParam(value="New dependant version with the same format as the effectiveTime RF2 field, for example '20190731'") @PathVariable final Integer newDependantVersion) throws BusinessServiceException {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();

		String jobId = codeSystemUpgradeService.upgrade(shortName, newDependantVersion);
		codeSystemUpgradeService.waitForCodeSystemUpgradeToComplete(jobId, SecurityContextHolder.getContext());

		String requestUrl = request.getRequestURL().toString();
		requestUrl = requestUrl.replace("/" + shortName, "").replace("/" + newDependantVersion, "");
		return new ResponseEntity<>(ControllerHelper.getCreatedLocationHeaders(requestUrl + "/", jobId, null), HttpStatus.CREATED);
	}


	@ApiOperation(value = "Retrieve an upgrade job.",
			notes = "Retrieves the state of an upgrade job. Used to view the upgrade configuration and check its status.")
	@GetMapping(value = "/upgrade/{jobId}")
	public CodeSystemUpgradeJob getUpgradeJob(@PathVariable String jobId) throws RestClientException {
		return codeSystemUpgradeService.getUpgradeJob(jobId);
	}
}
