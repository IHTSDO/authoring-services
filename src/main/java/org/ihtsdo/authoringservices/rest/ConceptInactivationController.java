package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest;
import org.ihtsdo.authoringservices.domain.ProcessStatus;
import org.ihtsdo.authoringservices.service.ConceptInactivationService;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.PROJECT_KEY;
import static org.ihtsdo.authoringservices.rest.ControllerHelper.TASK_KEY;
import static org.ihtsdo.authoringservices.rest.ControllerHelper.requiredParam;

@Tag(name = "Concept Inactivation")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class ConceptInactivationController {

	private final ConceptInactivationService conceptInactivationService;

	public ConceptInactivationController(ConceptInactivationService conceptInactivationService) {
		this.conceptInactivationService = conceptInactivationService;
	}

	@Operation(summary = "Inactivate a concept on a task branch",
			description = "Validates task write access and CRS blocking, fetches the concept plus accepted affected concepts, "
					+ "applies inactivation/replacements from the request, and bulk-updates via Snowstorm. "
					+ "When dryRun is true, the prepared concepts are returned synchronously without writing. "
					+ "Otherwise the update runs asynchronously: the request returns 202 Accepted, progress is available "
					+ "from the status endpoint and a WebSocket notification is sent on completion or failure.")
	@ApiResponse(responseCode = "200", description = "Dry run: prepared concepts")
	@ApiResponse(responseCode = "202", description = "Inactivation started")
	@PostMapping(value = "/projects/{projectKey}/tasks/{taskKey}/concepts/{conceptId}/inactivate")
	public ResponseEntity<List<ConceptPojo>> inactivateConcept(@PathVariable String projectKey,
			@PathVariable String taskKey,
			@PathVariable String conceptId,
			@RequestParam(value = "dryRun", required = false) Boolean dryRun,
			@RequestBody(required = false) ConceptInactivationRequest request) throws BusinessServiceException {
		requiredParam(projectKey, PROJECT_KEY);
		requiredParam(taskKey, TASK_KEY);
		requiredParam(conceptId, "conceptId");

		boolean isDryRun = dryRun != null ? dryRun : request != null && request.isDryRun();
		if (isDryRun) {
			return ResponseEntity.ok(conceptInactivationService.previewInactivation(projectKey, taskKey, conceptId, request));
		}
		conceptInactivationService.inactivate(projectKey, taskKey, conceptId, request);
		return ResponseEntity.accepted().build();
	}

	@Operation(summary = "Retrieve the status of a concept inactivation on a task branch")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value = "/projects/{projectKey}/tasks/{taskKey}/concepts/{conceptId}/inactivate/status")
	public ProcessStatus getInactivationStatus(@PathVariable String projectKey,
			@PathVariable String taskKey,
			@PathVariable String conceptId) {
		return conceptInactivationService.getInactivationStatus(
				requiredParam(projectKey, PROJECT_KEY),
				requiredParam(taskKey, TASK_KEY),
				requiredParam(conceptId, "conceptId"));
	}
}
