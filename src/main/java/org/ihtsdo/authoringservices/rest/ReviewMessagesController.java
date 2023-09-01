package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.entity.ReviewMessage;
import org.ihtsdo.authoringservices.domain.ReviewConcept;
import org.ihtsdo.authoringservices.domain.ReviewMessageCreateRequest;
import org.ihtsdo.authoringservices.service.ReviewService;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Tag(name = "Review Messages")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ReviewMessagesController {

	@Autowired
	private ReviewService reviewService;

	@Operation(summary = "Retrieve a list of stored details for a task review concept, including last view date for the user and a list of messages.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review", method= RequestMethod.GET)
	public List<ReviewConcept> retrieveTaskReview(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {

		return reviewService.retrieveTaskReviewConceptDetails(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), SecurityUtil.getUsername());
	}

	@Operation(summary = "Retrieve a list of stored details for a project review concept, including last view date for the user and a list of messages.")
	@ApiResponse(responseCode = "200",description = "OK")
	@RequestMapping(value="/projects/{projectKey}/review", method= RequestMethod.GET)
	public List<ReviewConcept> retrieveProjectReview(@PathVariable final String projectKey) throws BusinessServiceException {

		return reviewService.retrieveProjectReviewConceptDetails(requiredParam(projectKey, PROJECT_KEY), SecurityUtil.getUsername());
	}

	@Operation(summary = "Record a review feedback message on task concepts.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/message", method= RequestMethod.POST)
	public ReviewMessage postTaskReviewMessage(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody ReviewMessageCreateRequest createRequest) throws BusinessServiceException {

		return reviewService.postReviewMessage(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), createRequest, SecurityUtil.getUsername());
	}

	@Operation(summary = "Record a review feedback message on project concepts.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/review/message", method= RequestMethod.POST)
	public ReviewMessage postProjectReviewMessage(@PathVariable final String projectKey,
			@RequestBody ReviewMessageCreateRequest createRequest) throws BusinessServiceException {

		return reviewService.postReviewMessage(requiredParam(projectKey, PROJECT_KEY), null, createRequest, SecurityUtil.getUsername());
	}

	@Operation(summary = "Mark a task review concept as viewed for this user.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/concepts/{conceptId}/view", method= RequestMethod.POST)
	public void markTaskReviewConceptViewed(@PathVariable final String projectKey, @PathVariable final String taskKey, @PathVariable final String conceptId) {
		reviewService.recordConceptView(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), conceptId, SecurityUtil.getUsername());
	}

	@Operation(summary = "Mark a project review concept as viewed for this user.")
	@ApiResponse(responseCode = "200", description = "OK")
	@RequestMapping(value="/projects/{projectKey}/review/concepts/{conceptId}/view", method= RequestMethod.POST)
	public void markProjectReviewConceptViewed(@PathVariable final String projectKey, @PathVariable final String conceptId) {
		reviewService.recordConceptView(requiredParam(projectKey, PROJECT_KEY), null, conceptId, SecurityUtil.getUsername());
	}

}
