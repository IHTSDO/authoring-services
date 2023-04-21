package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob.UpgradeStatus.*;

@Service
public class CodeSystemService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String DEFAULT_JIRA_PROJECT = "MSSP";
	private static final String DEFAULT_ISSUE_TYPE = "Service Request";
	private static final String SHARED = "SHARED";
	private static final String UPGRADE_JOB_PANEL_ID = "code-system-upgrade-job";

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private UiStateService uiStateService;

	@Autowired
	private TaskService taskService;

	@Autowired
	private  BranchService branchService;

	@Autowired
	private SnowstormClassificationClient classificationService;

	@Autowired
	private ValidationService validationService;

	@Autowired
	@Qualifier("validationTicketOAuthJiraClient")
	private ImpersonatingJiraClientFactory jiraClientFactory;

	public List<AuthoringCodeSystem> findAll() throws BusinessServiceException {
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystems();
		return buildAuthoringCodeSystems(codeSystems);
	}

	public String upgrade(String shortName, Integer newDependantVersion) throws BusinessServiceException {
		String location = snowstormRestClientFactory.getClient().upgradeCodeSystem(shortName, newDependantVersion, false);
		return location.substring(location.lastIndexOf("/") + 1);
	}

	public CodeSystemUpgradeJob getUpgradeJob(String jobId) throws RestClientException {
		return snowstormRestClientFactory.getClient().getCodeSystemUpgradeJob(jobId);
	}

	public void lockProjects(String codeSystemShortame) throws BusinessServiceException, JiraException {
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystems();
		CodeSystem cs = codeSystems.stream().filter(item -> item.getShortName().equals(codeSystemShortame)).findAny().orElse(null);
		if (cs == null) {
			throw new BusinessServiceException("Code system with shortname " + codeSystemShortame + " not found");
		}
		List<AuthoringProject> projects = taskService.listProjects(true);
		projects = projects.stream().filter(project -> project.getBranchPath().substring(0, project.getBranchPath().lastIndexOf("/")).equals(cs.getBranchPath())).collect(Collectors.toList());
		List<String> failedToLockProjects = new ArrayList<>();
		for (AuthoringProject project : projects) {
			try {
				taskService.lockProject(project.getKey());
			} catch (Exception e) {
				logger.error("Failed to lock the project " + project.getKey(), e);
				failedToLockProjects.add(project.getKey());
			}
		}
		if (failedToLockProjects.size() != 0) {
			throw new BusinessServiceException(String.format("The following projects %s failed to lock. Please contact technical support to get help", failedToLockProjects.toString()));
		}
	}

	public void unlockProjects(String codeSystemShortame) throws BusinessServiceException, JiraException {
		List<CodeSystem> codeSystems = snowstormRestClientFactory.getClient().getCodeSystems();
		CodeSystem cs = codeSystems.stream().filter(item -> item.getShortName().equals(codeSystemShortame)).findAny().orElse(null);
		if (cs == null) {
			throw new BusinessServiceException("Code system with shortname " + codeSystemShortame + " not found");
		}
		List<AuthoringProject> projects = taskService.listProjects(true);
		projects = projects.stream().filter(project -> project.getBranchPath().substring(0, project.getBranchPath().lastIndexOf("/")).equals(cs.getBranchPath())).collect(Collectors.toList());
		List<String> failedToUnlockProjects = new ArrayList<>();
		for (AuthoringProject project : projects) {
			try {
				taskService.unlockProject(project.getKey());
			} catch (Exception e) {
				logger.error("Failed to unlock the project " + project.getKey(), e);
				failedToUnlockProjects.add(project.getKey());
			}
		}
		if (failedToUnlockProjects.size() != 0) {
			throw new BusinessServiceException(String.format("The following projects %s failed to unlock. Please contact technical support to get help.", failedToUnlockProjects.toString()));
		}
	}

	@Async
	public void waitForCodeSystemUpgradeToComplete(String jobId, Boolean generateEn_GbLanguageRefsetDelta, String projectKey, SecurityContext securityContext) throws BusinessServiceException {
		SecurityContextHolder.setContext(securityContext);
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		CodeSystemUpgradeJob codeSystemUpgradeJob;
		int sleepSeconds = 5;
		int totalWait = 0;
		int maxTotalWait = 2 * 60 * 60;
		try {
			do {
				Thread.sleep(1000 * sleepSeconds);
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
					if (COMPLETED.equals(codeSystemUpgradeJob.getStatus()) && Boolean.TRUE.equals(generateEn_GbLanguageRefsetDelta) && isIntegrityCheckEmpty(codeSystem.getBranchPath())) {
						String projectBranchPath = taskService.getProjectBranchPathUsingCache(projectKey);
						Merge merge = null;
						try {
							merge = branchService.mergeBranchSync(codeSystem.getBranchPath(), projectBranchPath, null);
						} catch (Exception e) {
							logger.error("Failed to rebase the project " + projectKey + ". Error: " + e.getMessage());
						}
						if (merge != null) {
							if (merge.getStatus() == Merge.Status.COMPLETED) {
								AuthoringTaskCreateRequest taskCreateRequest = new AuthoringTask();
								taskCreateRequest.setSummary("en-GB Import " + newDependantVersionISOFormat);

								AuthoringTask task = taskService.createTask(projectKey, taskCreateRequest);
								if (client.getBranch(task.getBranchPath()) == null) {
									client.createBranch(task.getBranchPath());
								}
								try {
									client.generateAdditionalLanguageRefsetDelta(codeSystemShortname, task.getBranchPath(), "900000000000508004", false);
								} catch (Exception e) {
									logger.error("Failed to generate additional language refset delta", e);
								}
							} else {
								ApiError apiError = merge.getApiError();
								String message = apiError != null ? apiError.getMessage() : null;
								logger.error("Failed to rebase the project " + projectKey + ". Error: " + message);
							}
						}
					}

					// Raise an JIRA ticket for SI to update the daily build
					createJiraIssue(codeSystem.getName().replace("Edition","Extension"), newDependantVersionISOFormat, generateDescription(codeSystem, codeSystemUpgradeJob, newDependantVersionISOFormat));
				}

				try {
					uiStateService.deleteTaskPanelState(codeSystemShortname, codeSystemShortname, SHARED, UPGRADE_JOB_PANEL_ID);
				} catch (Exception e) {
					logger.error("Failed to delete the UI panel with id " + UPGRADE_JOB_PANEL_ID, e);
				}
			}
		} catch (InterruptedException | RestClientException e) {
			throw new BusinessServiceException("Failed to fetch code system upgrade status.", e);
		}
	}

	private Issue createJiraIssue(String codeSystemName, String newDependantVersion, String description) throws BusinessServiceException {
		Issue jiraIssue;

		try {
			jiraIssue = getJiraClient().createIssue(DEFAULT_JIRA_PROJECT, DEFAULT_ISSUE_TYPE)
					.field(Field.SUMMARY, "Upgraded " + codeSystemName + " to the new " + newDependantVersion + " International Edition")
					.field(Field.DESCRIPTION, description)
					.execute();

			logger.info("New INFRA ticket with key {}", jiraIssue.getKey());
			final Issue.FluentUpdate updateRequest = jiraIssue.update();
			updateRequest.field(Field.ASSIGNEE, "");

			updateRequest.execute();
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to create Jira task. Error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
		}

		return jiraIssue;
	}

	private String generateDescription(CodeSystem codeSystem, CodeSystemUpgradeJob codeSystemUpgradeJob, String newDependantVersion) {
		StringBuilder result = new StringBuilder();
		result.append("This ticket has been automatically generated as the NRC has upgraded their extension to a new release.").append("\n").append("\n");
		result.append("Author: ").append(getUsername()).append("\n")
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
		result.append("Only proceed If there are no integrity failures/any failures have been resolved - Disable the Daily Build until the integrity failures are resolved by the NRC.").append("\n");
		result.append("2. Reconfigure the extension daily build.").append("\n");
		result.append("3. Run the Multiple Modules Axioms Report on the Extension flagging any results within the report to the NRC to be fixed.");

		return result.toString();
	}

	private boolean isIntegrityCheckEmpty(String branchPath) {
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		IntegrityIssueReport report = client.integrityCheck(branchPath);
		return report.isEmpty();
	}

	private JiraClient getJiraClient() {
		return jiraClientFactory.getImpersonatingInstance(getUsername());
	}

	private String getUsername() {
		return SecurityUtil.getUsername();
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
						&& branch.getHeadTimestamp() != validation.getContentHeadTimestamp().longValue()) {
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
}
