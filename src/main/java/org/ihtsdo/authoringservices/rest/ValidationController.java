package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.ihtsdo.authoringservices.domain.ReleaseRequest;
import org.ihtsdo.authoringservices.domain.Status;
import org.ihtsdo.authoringservices.service.ValidationService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

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
		return validationService.startValidation(releaseRequest);
	}

	@ApiOperation(value = "Initiate validation on a Task")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.POST)
	public Status startValidation(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestParam(value = "enableMRCMValidation", required = false, defaultValue = "false") boolean enableMRCMValidation) throws BusinessServiceException {
		return validationService.startValidation(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), enableMRCMValidation);
	}

	@ApiOperation(value = "Recover the most recent validation on a Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return validationService.getValidationJson(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
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
		return validationService.startValidation(requiredParam(projectKey, PROJECT_KEY), null, true);
	}

	@ApiOperation(value = "Recover the most recent validation on Project")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey) throws BusinessServiceException {
		return validationService.getValidationJson(requiredParam(projectKey, PROJECT_KEY));
	}

	@ApiOperation(value = "Refresh validation status cache (workaround) where the statuses have been modified in database manually")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/validation/statuses/refresh", method = RequestMethod.POST)
	public void refreshValidationStatusCache() {
		validationService.refreshValidationStatusCache();
	}

	@ApiOperation(value = "Reset branch validation status to default NOT TRIGGER (workaround) where the RVF gets stuck")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/validation/{branchPath}/status/reset", method = RequestMethod.POST)
	public void resetValidationStatusCacheForBranch(@PathVariable String branchPath) {
		validationService.resetBranchValidationStatus(BranchPathUriUtil.parseBranchPath(branchPath));
	}

	@ApiOperation(value = "Retrieve all author assertion UUIDs")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/author-issue-items", method = RequestMethod.GET)
	public Set<String> getAuthorIssues() {
		return validationService.getAuthorItems();
	}

	@ApiOperation(value = "Insert new author assertion UUIDs")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/author-issue-items", method = RequestMethod.POST)
	public Set<String> addNewAuthorIssues(@RequestBody Map<String, Set <String>> assertionUUIDs) throws IOException {
		validationService.insertAuthorItems(assertionUUIDs.get(assertionUUIDs.keySet().iterator().next()));
		return validationService.getAuthorItems();
	}

	@ApiOperation(value = "Remove an author assertion UUID")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/author-issue-items/{uuid}", method = RequestMethod.DELETE)
	public void removeAuthorIssue(@PathVariable String uuid) throws IOException {
		validationService.deleteAuthorItem(uuid);
	}
}
