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
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.jetbrains.annotations.Nullable;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob.UpgradeStatus.*;

@Service
public class CodeSystemService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String CODE_SYSTEM_NOT_FOUND_MSG = "Code system with shortname %s not found";
	private static final String SHARED = "SHARED";
	private static final String UPGRADE_JOB_PANEL_ID = "code-system-upgrade-job"; // this panel ID will be persisted by the frontend

	private static final String AUTHORING_FREEZE = "authoringFreeze";

	@Value("${jira.project.issue.type}")
	private String jiraIssueType;

	@Value("${jira.cloud.managed-service.upgrade.project-key}")
	private String projectKey;

	@Value("${email.link.platform.url}")
	private String platformUrl;

	@Value("${jira.cloud.reporter-accountid}")
	private String reporter;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private UiStateService uiStateService;

	@Autowired
	private TaskServiceFactory taskServiceFactory;

	@Autowired
	private ProjectServiceFactory projectServiceFactory;

	@Autowired
	private  BranchService branchService;

	@Autowired
	private SnowstormClassificationClient classificationService;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private RebaseService rebaseService;

	@Autowired
	private JiraCloudClient jiraCloudClient;

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

	public String upgrade(String shortName, Integer newDependantVersion) throws BusinessServiceException, ServiceException {
		SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();

		List<CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();
		CodeSystem cs = codeSystems.stream().filter(item -> item.getShortName().equals(shortName)).findFirst().orElse(null);
		if (cs == null) {
			throw new BusinessServiceException(String.format(CODE_SYSTEM_NOT_FOUND_MSG, shortName));
		}

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
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystems();
		CodeSystem cs = codeSystems.stream().filter(item -> item.getShortName().equals(codeSystemShortname)).findAny().orElse(null);
		if (cs == null) {
			throw new BusinessServiceException(String.format(CODE_SYSTEM_NOT_FOUND_MSG, codeSystemShortname));
		}
		List<AuthoringProject> authoringProjects = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(true, null, null));
		List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(true, null, null);
		ProjectFilterUtil.joinJiraProjectsIfNotExists(jiraProjects, authoringProjects);

		authoringProjects = authoringProjects.stream().filter(project -> project.getBranchPath().substring(0, project.getBranchPath().lastIndexOf("/")).equals(cs.getBranchPath())).toList();
		List<String> failedToLockProjects = new ArrayList<>();
		for (AuthoringProject project : authoringProjects) {
			try {
				projectServiceFactory.getInstanceByKey(project.getKey()).lockProject(project.getKey());
			} catch (Exception e) {
				logger.error("Failed to lock the project " + project.getKey(), e);
				failedToLockProjects.add(project.getKey());
			}
		}
		if (!failedToLockProjects.isEmpty()) {
			throw new BusinessServiceException(String.format("The following projects %s failed to lock. Please contact technical support to get help", failedToLockProjects.toString()));
		}
	}

	public void unlockProjects(String codeSystemShortname) throws BusinessServiceException {
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystems();
		CodeSystem cs = codeSystems.stream().filter(item -> item.getShortName().equals(codeSystemShortname)).findAny().orElse(null);
		if (cs == null) {
			throw new BusinessServiceException(String.format(CODE_SYSTEM_NOT_FOUND_MSG, codeSystemShortname));
		}
		List<AuthoringProject> authoringProjects = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(true, null, null));
		List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(true, null, null);
		ProjectFilterUtil.joinJiraProjectsIfNotExists(jiraProjects, authoringProjects);

		authoringProjects = authoringProjects.stream().filter(project -> project.getBranchPath().substring(0, project.getBranchPath().lastIndexOf("/")).equals(cs.getBranchPath())).toList();
		List<String> failedToUnlockProjects = new ArrayList<>();
		for (AuthoringProject project : authoringProjects) {
			try {
				projectServiceFactory.getInstanceByKey(project.getKey()).unlockProject(project.getKey());
			} catch (Exception e) {
				logger.error("Failed to unlock the project " + project.getKey(), e);
				failedToUnlockProjects.add(project.getKey());
			}
		}
		if (!failedToUnlockProjects.isEmpty()) {
			throw new BusinessServiceException(String.format("The following projects %s failed to unlock. Please contact technical support to get help.", failedToUnlockProjects.toString()));
		}
	}

	@Async
	public void waitForCodeSystemUpgradeToComplete(String jobId, Boolean generateEnGbLanguageRefsetDelta, String projectKey, SecurityContext securityContext) throws BusinessServiceException {
		SecurityContextHolder.setContext(securityContext);
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		CodeSystemUpgradeJob codeSystemUpgradeJob;
		int sleepSeconds = 5;
		int totalWait = 0;
		int maxTotalWait = 2 * 60 * 60;
		try {
			do {
				Thread.sleep(1000L * sleepSeconds);
				totalWait += sleepSeconds;
				codeSystemUpgradeJob = client.getCodeSystemUpgradeJob(jobId);
			} while (totalWait < maxTotalWait && RUNNING.equals(codeSystemUpgradeJob.getStatus()));

			if (codeSystemUpgradeJob != null && (COMPLETED.equals(codeSystemUpgradeJob.getStatus()) || FAILED.equals(codeSystemUpgradeJob.getStatus()))) {
				List <CodeSystem> codeSystems = client.getCodeSystems();
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
					createJiraIssue(codeSystem.getName().replace("Edition","Extension"), newDependantVersionISOFormat, generateDescription(codeSystem, codeSystemUpgradeJob, newDependantVersionISOFormat));
				}

				deleteUpgradeJobPanelState(codeSystemShortname);
			}
		} catch (InterruptedException | RestClientException e) {
			logger.error("Failed to fetch code system upgrade status.", e);
			Thread.currentThread().interrupt();
		}
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

	@Nullable
	private Merge rebaseProject(String projectKey, CodeSystem codeSystem, String projectBranchPath) {
		Merge merge = null;
		try {
			merge = branchService.mergeBranchSync(codeSystem.getBranchPath(), projectBranchPath, null);
		} catch (Exception e) {
			logger.error("Failed to rebase the project {}. Error: {}", projectKey, e.getMessage());
		}
		return merge;
	}

	private void createJiraIssue(String codeSystemName, String newDependantVersion, String description) throws BusinessServiceException {
		try {
			JSONObject jiraIssue = jiraCloudClient.createIssue(projectKey, "Upgraded " + codeSystemName + " to the new " + newDependantVersion + " International Edition", description, jiraIssueType, reporter);
			String issueKey = jiraIssue.getString("key");
			logger.info("New JIRA ticket with key {} has been created", issueKey);
			JSONObject issueFields = new JSONObject();
			issueFields.put(Field.ASSIGNEE, "");
			jiraCloudClient.updateIssue(issueKey, issueFields);
		} catch (IOException e) {
			throw new BusinessServiceException("Failed to create Jira task. Error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
		}
    }

	private String generateDescription(CodeSystem codeSystem, CodeSystemUpgradeJob codeSystemUpgradeJob, String newDependantVersion) {
		StringBuilder result = new StringBuilder();
		result.append("This ticket has been automatically generated as the NRC has upgraded their extension to a new release.").append("\n").append("\n");
		result.append("Author: ").append(getUsername()).append("\n")
				.append("New version: ").append(newDependantVersion).append("\n")
				.append("Environment: ").append(getEnvironment()).append("\n")
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

	private String getUsername() {
		return SecurityUtil.getUsername();
	}

	private String getEnvironment() {
		URI uri;
		try {
			uri = new URI(platformUrl);
		} catch (URISyntaxException e) {
			logger.error("Failed to detect environment", e);
			return null;
		}
		String domain = uri.getHost();
		domain = domain.startsWith("www.") ? domain.substring(4) : domain;
		return (domain.contains("-") ? domain.substring(0, domain.lastIndexOf("-")) : domain.substring(0, domain.indexOf("."))).toUpperCase();
	}

	private List<AuthoringCodeSystem> buildAuthoringCodeSystems(List<CodeSystem> codeSystems) throws BusinessServiceException {
		List<AuthoringCodeSystem> allCodeSystems = new ArrayList <>();
		try {
			for (CodeSystem codeSystem : codeSystems) {
				AuthoringCodeSystem authoringCodeSystem = new AuthoringCodeSystem(codeSystem);
				authoringCodeSystem.setLatestClassificationJson(classificationService.getLatestClassification(codeSystem.getBranchPath()));

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
		List<AuthoringProject> authoringProjects = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(true, true, null));
		List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(true, true, null);
		ProjectFilterUtil.joinJiraProjectsIfNotExists(jiraProjects, authoringProjects);

		// Filter by code system branch path and provided project keys
		authoringProjects = authoringProjects.stream()
			.filter(project -> project.getBranchPath().substring(0, project.getBranchPath().lastIndexOf("/")).equals(codeSystem.getBranchPath()))
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
}
