package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.domain.ReleaseRequest;
import org.ihtsdo.authoringservices.domain.Status;
import org.ihtsdo.authoringservices.service.ValidationService;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Api("validation")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ValidationController {

	@Autowired
	private ValidationService validationService;
	
	@ApiOperation(value = "Initiate validation on MAIN")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/main/validation", method = RequestMethod.POST)
	public Status startValidation( @RequestBody(required=false) final ReleaseRequest releaseRequest) throws BusinessServiceException {
		return validationService.startValidation(releaseRequest, SecurityUtil.getUsername(), SecurityUtil.getAuthenticationToken());
	}

	@ApiOperation(value = "Initiate validation on a Task")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.POST)
	public Status startValidation(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestParam(value = "enableMRCMValidation", required = false, defaultValue = "false") boolean enableMRCMValidation) throws BusinessServiceException {
		return validationService.startValidation(projectKey, taskKey, enableMRCMValidation, SecurityUtil.getUsername(), SecurityUtil.getAuthenticationToken());
	}

	@ApiOperation(value = "Recover the most recent validation on a Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return validationService.getValidationJson(projectKey, taskKey);
	}
	
	@ApiOperation(value = "Recover the most recent validation on Main")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value = "/main/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation() throws BusinessServiceException {
		return validationService.getValidationJson();
	}
	
	@ApiOperation(value = "Initiate validation on a Project")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/validation", method = RequestMethod.POST)
	public Status startValidation(@PathVariable final String projectKey) throws BusinessServiceException {
		return validationService.startValidation(projectKey, SecurityUtil.getUsername(), SecurityUtil.getAuthenticationToken());
	}

	@ApiOperation(value = "Recover the most recent validation on Project")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey) throws BusinessServiceException {
		return validationService.getValidationJson(projectKey);
	}

	@ApiOperation(value = "Clear validation status cache (workaround)")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/main/validation/clear-status-cache", method = RequestMethod.POST)
	public void clearValidationStatusCache() {
		validationService.clearStatusCache();
	}

}
