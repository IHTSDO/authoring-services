package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.Classification;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.CacheService;
import org.ihtsdo.authoringservices.service.SnowstormClassificationClient;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import us.monoid.json.JSONException;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Tag(name = "Classification")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ClassificationController {

	private static final String FAILED_TO_START_CLASSIFICATION_ERROR_MSG = "Failed to start classification.";

	@Autowired
	private BranchService branchService;

	@Autowired
	private SnowstormClassificationClient snowstormClassificationClient;

	@Autowired
	private CacheService cacheService;

	@Operation(summary = "Start classification on a project")
	@PostMapping(value = "/projects/{projectKey}/classifications")
	public Classification startProjectClassification(@PathVariable String projectKey) throws BusinessServiceException {
		String branchPath = branchService.getProjectBranchPathUsingCache(requiredParam(projectKey, PROJECT_KEY));
		try {
			Classification classification = snowstormClassificationClient.startClassification(projectKey, null, branchPath, SecurityUtil.getUsername());
			cacheService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException(FAILED_TO_START_CLASSIFICATION_ERROR_MSG, e);
		}
	}

	@Operation(summary = "Start classification for a branch")
	@PostMapping(value = "/branches/{branch}/classifications")
	public Classification startBranchClassification(@PathVariable String branch) throws BusinessServiceException {
		String branchPath = BranchPathUriUtil.decodePath(branch);
		try {
			Classification classification = snowstormClassificationClient.startClassification(null, null, branchPath, SecurityUtil.getUsername());
			cacheService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException(FAILED_TO_START_CLASSIFICATION_ERROR_MSG, e);
		}
	}

	@Operation(summary = "Start classification on a task")
	@PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/classifications")
	public Classification startTaskClassification(@PathVariable String projectKey, @PathVariable String taskKey) throws BusinessServiceException {
		String branchPath = branchService.getTaskBranchPathUsingCache(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		try {
			Classification classification = snowstormClassificationClient.startClassification(projectKey, taskKey, branchPath, SecurityUtil.getUsername());
			cacheService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException(FAILED_TO_START_CLASSIFICATION_ERROR_MSG, e);
		}
	}

	@Operation(summary = "Clear classification status cache for project")
	@PostMapping(value = "/projects/{projectKey}/classifications/status/cache-evict")
	public ResponseEntity<String> clearProjectClassificationStatusCache(@PathVariable String projectKey) throws BusinessServiceException {
		String branchPath = branchService.getProjectBranchPathUsingCache(requiredParam(projectKey, "projectKey"));
		cacheService.clearClassificationCache(branchPath);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(summary = "Clear classification status cache for task")
	@PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/classifications/status/cache-evict")
	public ResponseEntity<String> clearTaskClassificationStatusCache(@PathVariable String projectKey, @PathVariable String taskKey) throws BusinessServiceException {
		String branchPath = branchService.getTaskBranchPathUsingCache(requiredParam(projectKey, "projectKey"), requiredParam(taskKey, "taskKey"));
		cacheService.clearClassificationCache(branchPath);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(summary = "Clear classification status cache for a branch")
	@PostMapping(value = "/branches/{branch}/classifications/status/cache-evict")
	public ResponseEntity<String> clearBranchClassificationStatusCache(@PathVariable String branch) {
		String branchPath = BranchPathUriUtil.decodePath(branch);
		cacheService.clearClassificationCache(branchPath);
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
