package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.Field;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.service.client.JiraCloudClient;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.authoringservices.service.util.ProjectFilterUtil;
import org.ihtsdo.authoringservices.service.util.TicketDescriptionContextUtil;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob.UpgradeStatus.*;

@Service
public class CodeSystemService {

	public static final String MANAGED_SERVICE_MAINTAINER_TYPE = "Managed Service";
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String CODE_SYSTEM_NOT_FOUND_MSG = "Code system with shortname %s not found";
	private static final String SHARED = "SHARED";
	private static final String UPGRADE_JOB_PANEL_ID = "code-system-upgrade-job"; // this panel ID will be persisted by the frontend

	private static final String AUTHORING_FREEZE = "authoringFreeze";

	@Value("${jira.project.issue.type}")
	private String jiraIssueType;

	@Value("${jira.cloud.project-key}")
	private String defaultProjectKey;

	@Value("${email.link.platform.url}")
	private String platformUrl;

	@Value("${jira.cloud.reporter-accountid}")
	private String reporter;

	private final SnowstormRestClientFactory snowstormRestClientFactory;

	private final UiStateService uiStateService;

	private final TaskServiceFactory taskServiceFactory;

	private final ProjectServiceFactory projectServiceFactory;

	private final BranchService branchService;

	private final SnowstormClassificationClient classificationService;

	private final ValidationService validationService;

	private final RebaseService rebaseService;

	private final JiraCloudClient jiraCloudClient;

	@Autowired
	public CodeSystemService(SnowstormRestClientFactory snowstormRestClientFactory, UiStateService uiStateService, TaskServiceFactory taskServiceFactory, ProjectServiceFactory projectServiceFactory,
							 BranchService branchService, SnowstormClassificationClient classificationService, ValidationService validationService, RebaseService rebaseService, JiraCloudClient jiraCloudClient) {
		this.snowstormRestClientFactory = snowstormRestClientFactory;
		this.uiStateService = uiStateService;
		this.taskServiceFactory = taskServiceFactory;
		this.projectServiceFactory = projectServiceFactory;
		this.branchService = branchService;
		this.classificationService = classificationService;
		this.validationService = validationService;
		this.rebaseService = rebaseService;
		this.jiraCloudClient = jiraCloudClient;
	}

	public AuthoringCodeSystem findOne(String shortname) throws BusinessServiceException, RestClientException {
		CodeSystem codeSystem = snowstormRestClientFactory.getClient().getCodeSystem(shortname);
		if (codeSystem != null) {
			List<AuthoringCodeSystem> authoringCodeSystems = buildAuthoringCodeSystems(Collections.singletonList(codeSystem));
			return authoringCodeSystems.get(0);
		}

		return null;
	}

	public List<CodeSystemVersion> getCodeSystemVersions(String shortname) {
		return snowstormRestClientFactory.getClient().getCodeSystemVersions(shortname, null, null);
	}

	public List<AuthoringCodeSystem> findAll() throws BusinessServiceException {
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystems();
		return buildAuthoringCodeSystems(codeSystems);
	}

	public List<AuthoringCodeSystem> findAllLightweight() throws BusinessServiceException {
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystemsLightweight();
		return buildAuthoringCodeSystems(codeSystems);
	}

	public String upgrade(String shortName, Integer newDependantVersion) throws BusinessServiceException, ServiceException {
		SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
		CodeSystem cs = getCodeSystemByShortnameOrThrow(shortName);

		final Map<String, Object> branchMetadata = branchService.getBranchMetadataIncludeInherited(cs.getBranchPath());
		boolean authoringFreeze = branchMetadata.containsKey(AUTHORING_FREEZE) && "true".equalsIgnoreCase((String) branchMetadata.get(AUTHORING_FREEZE));
		if (authoringFreeze) {
			throw new BusinessServiceException("Extension upgrade disabled during authoring freeze");
		}
		String location = snowstormRestClient.upgradeCodeSystem(shortName, newDependantVersion, true);
		return location.substring(location.lastIndexOf("/") + 1);
	}

	public CodeSystemUpgradeJob getUpgradeJob(String jobId) throws RestClientException {
		return snowstormRestClientFactory.getClient().getCodeSystemUpgradeJob(jobId);
	}

	public void lockProjects(String codeSystemShortname) throws BusinessServiceException {
		updateProjectLockState(codeSystemShortname, true);
	}

	public void unlockProjects(String codeSystemShortname) throws BusinessServiceException {
		updateProjectLockState(codeSystemShortname, false);
	}

	@Async
	public void waitForCodeSystemUpgradeToComplete(String jobId, Boolean generateEnGbLanguageRefsetDelta, String projectKey, SecurityContext securityContext) throws BusinessServiceException {
		SecurityContextHolder.setContext(securityContext);
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		CodeSystemUpgradeJob codeSystemUpgradeJob = null;
		int sleepSeconds = 10;
		int totalWait = 0;
		int maxTotalWait = 4 * 60 * 60;
		try {
			while (totalWait < maxTotalWait) {
				codeSystemUpgradeJob = client.getCodeSystemUpgradeJob(jobId);
				if (!RUNNING.equals(codeSystemUpgradeJob.getStatus())) {
					break;
				}
				if (sleepSeconds(sleepSeconds)) {
					totalWait = maxTotalWait;
				} else {
					totalWait += sleepSeconds;
				}
			}

			if (codeSystemUpgradeJob == null) {
				logger.error("Failed to retrieve the code system upgrade job");
				return;
			}

			// handle timeout
			if (RUNNING.equals(codeSystemUpgradeJob.getStatus())) {
				logger.error("Timeout waiting for job completion");
				return;
			}

			List <CodeSystem> codeSystems = client.getCodeSystemsLightweight();
			final String codeSystemShortname = codeSystemUpgradeJob.getCodeSystemShortname();
			CodeSystem codeSystem = codeSystems.stream().filter(c -> c.getShortName().equals(codeSystemShortname)).findFirst().orElse(null);

			if (codeSystem != null) {
				String newDependantVersionRF2Format = codeSystemUpgradeJob.getNewDependantVersion().toString();
				String newDependantVersionISOFormat = newDependantVersionRF2Format.substring(0, 4) + "-" + newDependantVersionRF2Format.substring(4, 6) + "-" + newDependantVersionRF2Format.substring(6, 8);

				// Generate additional EN_GB language refset
				if (COMPLETED.equals(codeSystemUpgradeJob.getStatus()) && Boolean.TRUE.equals(generateEnGbLanguageRefsetDelta) && isIntegrityCheckEmpty(codeSystem.getBranchPath())) {
					rebaseMainProjectAndGenerateAdditionalLanguageRefsetDelta(projectKey, codeSystem, newDependantVersionISOFormat, client, codeSystemShortname);
				}

				// Raise an JIRA ticket for SI to update the daily build
				createJiraIssue(codeSystem, newDependantVersionISOFormat, generateDescription(codeSystem, codeSystemUpgradeJob, newDependantVersionISOFormat));
			}

			deleteUpgradeJobPanelState(codeSystemShortname);
		} catch (RestClientException e) {
			logger.error("Failed to fetch code system upgrade status.", e);
			Thread.currentThread().interrupt();
		}
	}

	private boolean sleepSeconds(int sleepSeconds) {
		try {
			Thread.sleep(sleepSeconds * 1000L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // restore interrupt flag
			logger.error("Interrupted while waiting for job", e);
			return true;
		}
		return false;
	}

	private void rebaseMainProjectAndGenerateAdditionalLanguageRefsetDelta(String projectKey, CodeSystem codeSystem, String newDependantVersionISOFormat, SnowstormRestClient client, String codeSystemShortname) throws BusinessServiceException, RestClientException {
		String projectBranchPath = branchService.getProjectBranchPathUsingCache(projectKey);
		Merge merge = rebaseProject(projectKey, codeSystem, projectBranchPath);
		if (merge != null) {
			if (merge.getStatus() == Merge.Status.COMPLETED) {
				AuthoringTaskCreateRequest taskCreateRequest = new AuthoringTask();
				taskCreateRequest.setSummary("en-GB Import " + newDependantVersionISOFormat);

				boolean useNew = projectServiceFactory.getInstance(true).isUseNew(projectKey);
				AuthoringTask task = taskServiceFactory.getInstance(useNew).createTask(projectKey, SecurityUtil.getUsername(), taskCreateRequest, TaskType.AUTHORING);
				if (client.getBranch(task.getBranchPath()) == null) {
					client.createBranch(task.getBranchPath());
				}
				generateAdditionalLanguageRefsetDelta(client, codeSystemShortname, task);
			} else {
				ApiError apiError = merge.getApiError();
				String message = apiError != null ? apiError.getMessage() : null;
				logger.error("Failed to rebase the project {}. Error: {}", projectKey, message);
			}
		}
	}

	private void generateAdditionalLanguageRefsetDelta(SnowstormRestClient client, String codeSystemShortname, AuthoringTask task) {
		try {
			client.generateAdditionalLanguageRefsetDelta(codeSystemShortname, task.getBranchPath(), RF2Constants.GB_ENG_LANG_REFSET , false);
		} catch (Exception e) {
			logger.error("Failed to generate additional language refset delta", e);
		}
	}

	private void deleteUpgradeJobPanelState(String codeSystemShortname) {
		try {
			uiStateService.deleteTaskPanelState(codeSystemShortname, codeSystemShortname, SHARED, UPGRADE_JOB_PANEL_ID);
		} catch (Exception e) {
			logger.error("Failed to delete the UI panel with id " + UPGRADE_JOB_PANEL_ID, e);
		}
	}

	private Merge rebaseProject(String projectKey, CodeSystem codeSystem, String projectBranchPath) {
		Merge merge = null;
		try {
			merge = branchService.mergeBranchSync(codeSystem.getBranchPath(), projectBranchPath, null);
		} catch (Exception e) {
			logger.error("Failed to rebase the project {}. Error: {}", projectKey, e.getMessage());
		}
		return merge;
	}

	private void createJiraIssue(CodeSystem codeSystem, String newDependantVersion, String description) throws BusinessServiceException {
		try {
			String codeSystemName = codeSystem.getName().replace("Edition","Extension");
			String summary = "Upgraded " + codeSystemName + " to the new " + newDependantVersion + " International Edition";
			JSONObject jiraIssue;
			if (codeSystem.getMaintainerType().equals(MANAGED_SERVICE_MAINTAINER_TYPE)) {
				jiraIssue = jiraCloudClient.createServiceDeskIssue(summary, description, codeSystem.getCountryName());
				String issueKey = jiraIssue.getString("issueKey");
				logger.info("New Service Desk ticket with key {} has been created", issueKey);
			} else {
				jiraIssue = jiraCloudClient.createIssue(defaultProjectKey, summary, description, jiraIssueType, reporter);
				String issueKey = jiraIssue.getString("key");
				logger.info("New JIRA ticket with key {} has been created", issueKey);
				JSONObject issueFields = new JSONObject();
				issueFields.put(Field.ASSIGNEE, "");
				jiraCloudClient.updateIssue(issueKey, issueFields);
			}
		} catch (IOException e) {
			throw new BusinessServiceException("Failed to create Jira task. Error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
		}
    }

	private String generateDescription(CodeSystem codeSystem, CodeSystemUpgradeJob codeSystemUpgradeJob, String newDependantVersion) {
		StringBuilder result = new StringBuilder();
		TicketDescriptionContextUtil.appendEnvironmentAndUser(result, platformUrl, logger);

		result.append("This ticket has been automatically generated as the NRC has upgraded their extension to a new release.").append("\n")
				.append("New version: ").append(newDependantVersion).append("\n")
				.append("Branch Path: ").append(codeSystem.getBranchPath()).append("\n");
		if (COMPLETED.equals(codeSystemUpgradeJob.getStatus())) {
			result.append("Status: ").append(codeSystemUpgradeJob.getStatus());
			if (!isIntegrityCheckEmpty(codeSystem.getBranchPath())) {
				result.append(" with integrity failures");
			} else {
				result.append(" with no integrity failures");
			}
			result.append("\n");
		} else {
			result.append("Status: ").append(codeSystemUpgradeJob.getStatus()).append("\n");
		}

		if (FAILED.equals(codeSystemUpgradeJob.getStatus()) && StringUtils.hasLength(codeSystemUpgradeJob.getErrorMessage())) {
			result.append("Failure message: ").append(codeSystemUpgradeJob.getErrorMessage());
		}

		result.append("\n");
		result.append("Please follow the steps below:").append("\n");
		result.append("1. Check the Status above is 'COMPLETED' and action any integrity failures accordingly.").append("\n");
		result.append("Only proceed with the steps below if there are no integrity failures/any failures have been resolved - Disable the Daily Build until the integrity failures are resolved.").append("\n");
		result.append("2. Check if this extension requires the en_GB import, if so, the en_GB import needs to be run manually. The NRC need to review and promote this en_GB import task before continuing. If the import is not required, continue to the next step.").append("\n");
		result.append("3. Reconfigure the extension Daily Build.").append("\n");
		result.append("4. Run the Multiple Modules Axioms Report on the Extension flagging any results within the report to the NRC to be fixed.");

		return result.toString();
	}

	private boolean isIntegrityCheckEmpty(String branchPath) {
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		IntegrityIssueReport report = client.integrityCheck(branchPath);
		return report.isEmpty();
	}

	private List<AuthoringCodeSystem> buildAuthoringCodeSystems(List<CodeSystem> codeSystems) throws BusinessServiceException {
		List<AuthoringCodeSystem> allCodeSystems = new ArrayList <>();
		try {
			for (CodeSystem codeSystem : codeSystems) {
				AuthoringCodeSystem authoringCodeSystem = new AuthoringCodeSystem(codeSystem);
				authoringCodeSystem.setLatestClassification(classificationService.getLatestClassification(codeSystem.getBranchPath()));

				Branch branch = branchService.getBranchOrNull(codeSystem.getBranchPath());
				Validation validation = validationService.getValidation(codeSystem.getBranchPath());

				if (validation != null) {
					if (ValidationJobStatus.COMPLETED.name().equals(validation.getStatus())
						&& validation.getContentHeadTimestamp() != null
						&& branch.getHeadTimestamp() != validation.getContentHeadTimestamp()) {
						authoringCodeSystem.setLatestValidationStatus(ValidationJobStatus.STALE.name());
					} else {
						authoringCodeSystem.setLatestValidationStatus(validation.getStatus());
					}
					if (ValidationJobStatus.FAILED.name().equals(validation.getStatus())) {
						authoringCodeSystem.setValidationFailureMessages(validation.getFailureMessages());
					}
				}

				allCodeSystems.add(authoringCodeSystem);
			}
		} catch (ExecutionException | RestClientException | ServiceException e) {
			throw new BusinessServiceException("Failed to build code system list.", e);
		}
		return allCodeSystems;
	}

	@PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #codeSystem.branchPath)")
	public String rebaseProjects(AuthoringCodeSystem codeSystem, List<String> projectKeys) throws BusinessServiceException {
		String jobId = UUID.randomUUID().toString();
		
		// Get all projects and filter by the provided project keys
		List<AuthoringProject> authoringProjects = getProjectsOnBranch(codeSystem.getBranchPath(), true).stream()
			.filter(project -> projectKeys.contains(project.getKey()))
			.toList();
			
		for (AuthoringProject project : authoringProjects) {
			rebaseService.doProjectRebase(jobId, project);
		}

		return jobId;
	}

	public Map<String, ProcessStatus> getProjectRebaseStatuses(String jobId) {
		return rebaseService.getRebaseStatusByJobId(jobId);
	}

	private CodeSystem getCodeSystemByShortnameOrThrow(String codeSystemShortname) throws BusinessServiceException {
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystemsLightweight();
		return codeSystems.stream()
				.filter(item -> item.getShortName().equals(codeSystemShortname))
				.findAny()
				.orElseThrow(() -> new BusinessServiceException(String.format(CODE_SYSTEM_NOT_FOUND_MSG, codeSystemShortname)));
	}

	private void updateProjectLockState(String codeSystemShortname, boolean lock) throws BusinessServiceException {
		CodeSystem codeSystem = getCodeSystemByShortnameOrThrow(codeSystemShortname);
		List<AuthoringProject> authoringProjects = getProjectsOnBranch(codeSystem.getBranchPath(), null);

		List<String> failedProjects = new ArrayList<>();
		for (AuthoringProject project : authoringProjects) {
			try {
				if (lock) {
					projectServiceFactory.getInstanceByKey(project.getKey()).lockProject(project.getKey());
				} else {
					projectServiceFactory.getInstanceByKey(project.getKey()).unlockProject(project.getKey());
				}
			} catch (Exception e) {
				logger.error("Failed to {} the project {}", lock ? "lock" : "unlock", project.getKey(), e);
				failedProjects.add(project.getKey());
			}
		}

		if (!failedProjects.isEmpty()) {
			throw new BusinessServiceException(String.format(
					"The following projects %s failed to %s. Please contact technical support to get help%s",
					failedProjects,
					lock ? "lock" : "unlock",
					lock ? "" : "."));
		}
	}

	private List<AuthoringProject> getProjectsOnBranch(String codeSystemBranchPath, Boolean includeTasks) throws BusinessServiceException {
		List<AuthoringProject> authoringProjects = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(true, includeTasks, null));
		List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(true, includeTasks, null);
		ProjectFilterUtil.joinJiraProjectsIfNotExists(jiraProjects, authoringProjects);

		return authoringProjects.stream()
				.filter(project -> project.getBranchPath().substring(0, project.getBranchPath().lastIndexOf("/")).equals(codeSystemBranchPath))
				.toList();
	}
}
