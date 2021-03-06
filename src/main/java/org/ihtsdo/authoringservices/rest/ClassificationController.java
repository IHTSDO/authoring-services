package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.authoringservices.domain.Classification;
import org.ihtsdo.authoringservices.service.SnowstormClassificationClient;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import us.monoid.json.JSONException;

@Api("classification")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ClassificationController {

	@Autowired
	private TaskService taskService;

	@Autowired
	private SnowstormClassificationClient snowstormClassificationClient;

	@ApiOperation(value = "Start classification on a Project")
	@RequestMapping(value = "/projects/{projectKey}/classifications", method = RequestMethod.POST)
	@ResponseBody
	public Classification startProjectClassification(@PathVariable String projectKey) throws BusinessServiceException {
		String branchPath = taskService.getProjectBranchPathUsingCache(projectKey);
		try {
			Classification classification = snowstormClassificationClient.startClassification(projectKey, null, branchPath, SecurityUtil.getUsername());
			taskService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException("Failed to start classification.", e);
		}
	}

	@ApiOperation(value = "Start classification on a Task")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/classifications", method = RequestMethod.POST)
	@ResponseBody
	public Classification startTaskClassification(@PathVariable String projectKey, @PathVariable String taskKey) throws BusinessServiceException {
		String branchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		try {
			Classification classification = snowstormClassificationClient.startClassification(projectKey, taskKey, branchPath, SecurityUtil.getUsername());
			taskService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException("Failed to start classification.", e);
		}
	}

	@ApiOperation(value = "Clear classification status cache for Project")
	@RequestMapping(value = "/projects/{projectKey}/classifications/status/cache-evict", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> clearProjectClassificationStatusCache(@PathVariable String projectKey) throws BusinessServiceException {
		String branchPath = taskService.getProjectBranchPathUsingCache(projectKey);
		taskService.clearClassificationCache(branchPath);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@ApiOperation(value = "Clear classification status cache for Task")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/classifications/status/cache-evict", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> clearTaskClassificationStatusCache(@PathVariable String projectKey, @PathVariable String taskKey) throws BusinessServiceException {
		String branchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		taskService.clearClassificationCache(branchPath);
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
