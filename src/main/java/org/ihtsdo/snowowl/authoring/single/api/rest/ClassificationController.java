package org.ihtsdo.snowowl.authoring.single.api.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.service.ClassificationService;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import us.monoid.json.JSONException;

@Api("classification")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ClassificationController {

	@Autowired
	private TaskService taskService;

	@Autowired
	private ClassificationService classificationService;

	@ApiOperation(value = "Start classification on a Project")
	@RequestMapping(value = "/projects/{projectKey}/classifications", method = RequestMethod.POST)
	@ResponseBody
	public Classification startProjectClassification(@PathVariable String projectKey) throws BusinessServiceException {
		String branchPath = taskService.getProjectBranchPathUsingCache(projectKey);
		try {
			return classificationService.startClassification(projectKey, null, branchPath, ControllerHelper.getUsername());
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
			return classificationService.startClassification(projectKey, taskKey, branchPath, ControllerHelper.getUsername());
		} catch (RestClientException | JSONException e) {
			throw new BusinessServiceException("Failed to start classification.", e);
		}
	}

}
