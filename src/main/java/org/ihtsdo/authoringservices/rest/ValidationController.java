package org.ihtsdo.authoringservices.rest;

import com.google.common.collect.Sets;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.ReleaseRequest;
import org.ihtsdo.authoringservices.domain.Status;
import org.ihtsdo.authoringservices.entity.RVFFailureJiraAssociation;
import org.ihtsdo.authoringservices.service.CodeSystemService;
import org.ihtsdo.authoringservices.service.RVFFailureJiraAssociationService;
import org.ihtsdo.authoringservices.service.ValidationService;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.authoringservices.service.util.ProjectFilterUtil;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.ihtsdo.authoringservices.rest.ControllerHelper.*;

@Tag(name = "Validation")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class ValidationController {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ProjectServiceFactory projectServiceFactory;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private RVFFailureJiraAssociationService rvfFailureJiraAssociationService;

	@PostMapping(value = "/codesystems/validation/run-all")
	public Map<String, Set<String>> startValidationForAllCodeSystems( ) throws BusinessServiceException {
		Set<String> codeSystemShortnames = doValidateForAllCodeSystems();
		Map<String, Set<String>> response = new HashMap<>();
		response.put("items", codeSystemShortnames);
		return response;
	}

	@Operation(summary = "Initiate validation on MAIN")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/main/validation", method = RequestMethod.POST)
	public Status startValidation( @RequestBody(required=false) final ReleaseRequest releaseRequest) throws BusinessServiceException {
		return validationService.startValidation(releaseRequest);
	}

	@Operation(summary = "Initiate validation on a task")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.POST)
	public Status startValidation(@PathVariable final String projectKey, @PathVariable final String taskKey, @RequestParam(value = "enableMRCMValidation", required = false, defaultValue = "false") boolean enableMRCMValidation) throws BusinessServiceException {
		return validationService.startValidation(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY), enableMRCMValidation);
	}

	@Operation(summary = "Initiate validation for a given branch")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/branches/{branch}/validation", method = RequestMethod.POST)
	public Status startValidation(@PathVariable String branch, @RequestParam(value = "enableMRCMValidation", required = false, defaultValue = "false") boolean enableMRCMValidation) throws BusinessServiceException {
		return validationService.startValidation(BranchPathUriUtil.decodePath(branch), enableMRCMValidation);
	}

	@Operation(summary = "Recover the most recent validation for a branch")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/branches/{branch}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidationForBranch(@PathVariable String branch) throws BusinessServiceException {
		return validationService.getValidationJsonForBranch(BranchPathUriUtil.decodePath(branch));
	}

	@Operation(summary = "Recover the most recent validation on a task")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey, @PathVariable final String taskKey) throws BusinessServiceException {
		return validationService.getValidationJson(requiredParam(projectKey, PROJECT_KEY), requiredParam(taskKey, TASK_KEY));
	}

	@Operation(summary = "Recover the most recent validation on MAIN")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/main/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation() throws BusinessServiceException {
		return validationService.getValidationJson();
	}

	@Operation(summary = "Initiate validation on a project")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/projects/{projectKey}/validation", method = RequestMethod.POST)
	public Status startValidation(@PathVariable final String projectKey) throws BusinessServiceException {
		return validationService.startValidation(requiredParam(projectKey, PROJECT_KEY), null, true);
	}

	@Operation(summary = "Recover the most recent validation on a project")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/projects/{projectKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey) throws BusinessServiceException {
		return validationService.getValidationJson(requiredParam(projectKey, PROJECT_KEY));
	}

	@Operation(summary = "Refresh validation status cache (workaround) where the statuses have been modified in database manually")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/validation/statuses/refresh", method = RequestMethod.POST)
	public void refreshValidationStatusCache() {
		validationService.refreshValidationStatusCache();
	}

	@Operation(summary = "Reset branch validation status to default NOT TRIGGER (workaround) where the RVF gets stuck")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/validation/{branchPath}/status/reset", method = RequestMethod.POST)
	public void resetValidationStatusCacheForBranch(@PathVariable String branchPath) {
		validationService.resetBranchValidationStatus(BranchPathUriUtil.decodePath(branchPath));
	}

	@Operation(summary = "Retrieve all technical assertion UUIDs")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/technical-issue-items", method = RequestMethod.GET)
	public Set<String> getTechnicalIssues() {
		return validationService.getTechnicalItems();
	}

	@Operation(summary = "Insert new technical assertion UUIDs")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/technical-issue-items", method = RequestMethod.POST)
	public Set<String> addNewTechnicalIssues(@RequestBody String[] assertionUUIDs) throws IOException {
		validationService.insertTechnicalItems(Sets.newHashSet(assertionUUIDs));
		return validationService.getTechnicalItems();
	}

	@Operation(summary = "Remove a technical assertion UUID")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/technical-issue-items/{uuid}", method = RequestMethod.DELETE)
	public void removeTechnicalIssue(@PathVariable String uuid) throws IOException {
		validationService.deleteTechnicalItem(uuid);
	}

	@Operation(summary = "Insert new semantic tags into S3 file")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/semantic-tags", method = RequestMethod.POST)
	public void addNewSemanticTags(@RequestBody String[] semanticTags) throws IOException {
		validationService.insertSemanticTags(Sets.newHashSet(semanticTags));
	}

	@Operation(summary = "Remove a semantic tag from S3 file")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/semantic-tags", method = RequestMethod.DELETE)
	public void removeSemanticTag(@RequestParam String semanticTag) throws IOException {
		validationService.deleteSemanticTag(semanticTag);
	}

	@Operation(summary = "Raise JIRA tickets")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "CREATED")
	})
	@RequestMapping(value = "/branches/{branchPath}/validation-reports/{reportRunId}/failure-jira-associations", method = RequestMethod.POST)
	public ResponseEntity<Map<String, Object>> raiseJiraTickets(
			@PathVariable final String branchPath,
			@PathVariable final Long reportRunId,
			@RequestBody String[] assertionIds) throws IOException, BusinessServiceException, JiraException {
		return new ResponseEntity<>(rvfFailureJiraAssociationService.createFailureJiraAssociations(BranchPathUriUtil.decodePath(branchPath), reportRunId, assertionIds), HttpStatus.CREATED);
	}

	@Operation(summary = "Retrieve JIRA tickets for a given report")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value = "/validation-reports/{reportRunId}/failure-jira-associations", method = RequestMethod.GET)
	public ResponseEntity<List<RVFFailureJiraAssociation>> getJiraTickets(@PathVariable final Long reportRunId) {
		return new ResponseEntity<>(rvfFailureJiraAssociationService.findByReportRunId(reportRunId), HttpStatus.OK);
	}

	private Set<String> doValidateForAllCodeSystems() throws BusinessServiceException {
		SecurityContext context = SecurityContextHolder.getContext();
		List<AuthoringCodeSystem> codeSystems = codeSystemService.findAll();
		List<AuthoringProject> authoringProjects = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(true, null, null));
		List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(true, null, false);
		ProjectFilterUtil.joinJiraProjectsIfNotExists(jiraProjects, authoringProjects);

		Set<AuthoringCodeSystem> filteredCodeSystems = new HashSet<>();
		for(AuthoringCodeSystem codeSystem : codeSystems) {
			for(AuthoringProject project : authoringProjects) {
				String path = project.getBranchPath().substring(0, project.getBranchPath().lastIndexOf("/"));
				if(path.equals(codeSystem.getBranchPath()) && !filteredCodeSystems.contains(codeSystem)){
					filteredCodeSystems.add(codeSystem);
					break;
				}
			}
		}
		Set<String> codeSystemShortnames = filteredCodeSystems.stream().map(AuthoringCodeSystem::getShortName).collect(Collectors.toSet());
		logger.info("Total code systems to validate: {}. Items: {}", filteredCodeSystems.size(), codeSystemShortnames);
		filteredCodeSystems.stream().parallel().forEach(item -> {
			try {
				SecurityContextHolder.setContext(context);
				startValidation(item.getBranchPath(), true);
			} catch (BusinessServiceException e) {
				throw new RuntimeException(e);
			}
		});
		return codeSystemShortnames;
	}
}
