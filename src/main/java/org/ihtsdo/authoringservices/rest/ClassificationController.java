package org.ihtsdo.authoringservices.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Tag(name = "Classification")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ClassificationController {

	@Autowired
	private TaskService taskService;

	@Autowired
	private SnowstormClassificationClient snowstormClassificationClient;

	@Operation(summary = "Start classification on a project")
	@RequestMapping(value = "/projects/{projectKey}/classifications", method = RequestMethod.POST)
	@ResponseBody
	public Classification startProjectClassification(@PathVariable String projectKey) throws BusinessServiceException {
		String branchPath = taskService.getProjectBranchPathUsingCache(requiredParam(projectKey, PROJECT_KEY));
		try {
			Classification classification = snowstormClassificationClient.startClassification(projectKey, null, branchPath, SecurityUtil.getUsername());
			taskService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException("Failed to start classification.", e);
		}
	}

	@Operation(summary = "Start classification for a branch")
	@RequestMapping(value = "/branches/{branch}/classifications", method = RequestMethod.POST)
	@ResponseBody
	public Classification startBranchClassification(@PathVariable String branch) throws BusinessServiceException {
		String branchPath = BranchPathUriUtil.decodePath(branch);
		try {
			Classification classification = snowstormClassificationClient.startClassification(null, null, branchPath, SecurityUtil.getUsername());
			taskService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException("Failed to start classification.", e);
		}
	}

	@Operation(summary = "Start classification on a task")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/classifications", method = RequestMethod.POST)
	@ResponseBody
	public Classification startTaskClassification(@PathVariable String projectKey, @PathVariable String taskKey) throws BusinessServiceException {
		String branchPath = taskService.getTaskBranchPathUsingCache(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
		try {
			Classification classification = snowstormClassificationClient.startClassification(projectKey, taskKey, branchPath, SecurityUtil.getUsername());
			taskService.clearClassificationCache(branchPath);
			return classification;
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException("Failed to start classification.", e);
		}
	}

	@Operation(summary = "Clear classification status cache for project")
	@RequestMapping(value = "/projects/{projectKey}/classifications/status/cache-evict", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> clearProjectClassificationStatusCache(@PathVariable String projectKey) throws BusinessServiceException {
		String branchPath = taskService.getProjectBranchPathUsingCache(requiredParam(projectKey, "projectKey"));
		taskService.clearClassificationCache(branchPath);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(summary = "Clear classification status cache for task")
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/classifications/status/cache-evict", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<String> clearTaskClassificationStatusCache(@PathVariable String projectKey, @PathVariable String taskKey) throws BusinessServiceException {
		String branchPath = taskService.getTaskBranchPathUsingCache(requiredParam(projectKey, "projectKey"), requiredParam(taskKey, "taskKey"));
		taskService.clearClassificationCache(branchPath);
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
