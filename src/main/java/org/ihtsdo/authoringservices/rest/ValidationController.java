package org.ihtsdo.authoringservices.rest;

import com.google.common.collect.Sets;
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

	@ApiOperation(value = "Initiate validation for a given branch")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/branches/{branch}/validation", method = RequestMethod.POST)
	public Status startValidation(@PathVariable String branch, @RequestParam(value = "enableMRCMValidation", required = false, defaultValue = "false") boolean enableMRCMValidation) throws BusinessServiceException {
		return validationService.startValidation(BranchPathUriUtil.parseBranchPath(branch), enableMRCMValidation);
	}

	@ApiOperation(value = "Recover the most recent validation for a branch")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value = "/branches/{branch}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidationForBranch(@PathVariable String branch) throws BusinessServiceException {
		return validationService.getValidationJsonForBranch(BranchPathUriUtil.parseBranchPath(branch));
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

	@ApiOperation(value = "Retrieve all technical assertion UUIDs")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/technical-issue-items", method = RequestMethod.GET)
	public Set<String> getTechnicalIssues() {
		return validationService.getTechnicalItems();
	}

	@ApiOperation(value = "Insert new technical assertion UUIDs")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/technical-issue-items", method = RequestMethod.POST)
	public Set<String> addNewTechnicalIssues(@RequestBody String[] assertionUUIDs) throws IOException {
		validationService.insertTechnicalItems(Sets.newHashSet(assertionUUIDs));
		return validationService.getTechnicalItems();
	}

	@ApiOperation(value = "Remove a technical assertion UUID")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/technical-issue-items/{uuid}", method = RequestMethod.DELETE)
	public void removeTechnicalIssue(@PathVariable String uuid) throws IOException {
		validationService.deleteTechnicalItem(uuid);
	}

	@ApiOperation(value = "Insert new semantic tags into S3 file")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/semantic-tags", method = RequestMethod.POST)
	public void addNewSemanticTags(@RequestBody String[] semanticTags) throws IOException {
		validationService.insertSemanticTags(Sets.newHashSet(semanticTags));
	}

	@ApiOperation(value = "Remove a semantic tag from S3 file")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/semantic-tags", method = RequestMethod.DELETE)
	public void removeSemanticTag(@RequestParam String semanticTag) throws IOException {
		validationService.deleteSemanticTag(semanticTag);
	}
}
