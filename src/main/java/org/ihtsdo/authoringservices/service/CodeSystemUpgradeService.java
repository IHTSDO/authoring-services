package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.IntegrityIssueReport;
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

import java.util.List;

import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob.UpgradeStatus.*;

@Service
public class CodeSystemUpgradeService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String DEFAULT_INFRA_PROJECT = "INFRA";
	private static final String DEFAULT_ISSUE_TYPE = "Service Request";

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	@Qualifier("validationTicketOAuthJiraClient")
	private ImpersonatingJiraClientFactory jiraClientFactory;

	public String upgrade(String shortName, Integer newDependantVersion) throws BusinessServiceException {
		String location = snowstormRestClientFactory.getClient().upgradeCodeSystem(shortName, newDependantVersion, false);
		return location.substring(location.lastIndexOf("/") + 1);
	}

	public CodeSystemUpgradeJob getUpgradeJob(String jobId) throws RestClientException {
		return snowstormRestClientFactory.getClient().getCodeSystemUpgradeJob(jobId);
	}

	@Async
	public void waitForCodeSystemUpgradeToComplete(String jobId, SecurityContext securityContext) throws BusinessServiceException {
		SecurityContextHolder.setContext(securityContext);
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		CodeSystemUpgradeJob codeSystemUpgradeJob;
		int sleepSeconds = 4;
		int totalWait = 0;
		int maxTotalWait = 60 * 60;
		try {
			do {
				Thread.sleep(1000 * sleepSeconds);
				totalWait += sleepSeconds;
				codeSystemUpgradeJob = client.getCodeSystemUpgradeJob(jobId);
				if (sleepSeconds < 10) {
					sleepSeconds += 2;
				}
			} while (totalWait < maxTotalWait && RUNNING.equals(codeSystemUpgradeJob.getStatus()));

			if (codeSystemUpgradeJob != null && (COMPLETED.equals(codeSystemUpgradeJob.getStatus()) || FAILED.equals(codeSystemUpgradeJob.getStatus()))) {
				List <CodeSystem> codeSystems = client.getCodeSystems();
				final String codeSystemShortname = codeSystemUpgradeJob.getCodeSystemShortname();
				CodeSystem codeSystem = codeSystems.stream().filter(c -> c.getShortName().equals(codeSystemShortname)).findFirst().orElse(null);
				if (codeSystem != null) {
					String newDependantVersionRF2Format = codeSystemUpgradeJob.getNewDependantVersion().toString();
					String newDependantVersionISOFormat = newDependantVersionRF2Format.substring(0, 4) + "-" + newDependantVersionRF2Format.substring(4, 6) + "-" + newDependantVersionRF2Format.substring(6, 8);
					createJiraIssue(codeSystem.getName(), newDependantVersionISOFormat, generateDescription(codeSystem, codeSystemUpgradeJob, newDependantVersionISOFormat));
				}
			}
		} catch (InterruptedException | RestClientException e) {
			throw new BusinessServiceException("Failed to fetch code system upgrade status.", e);
		}
	}

	private Issue createJiraIssue(String codeSystemName, String newDependantVersion, String description) throws BusinessServiceException {
		Issue jiraIssue;

		try {
			jiraIssue = getJiraClient().createIssue(DEFAULT_INFRA_PROJECT, DEFAULT_ISSUE_TYPE)
					.field(Field.SUMMARY, "Upgrading " + codeSystemName + " to the new " + newDependantVersion + " International Edition")
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
		result.append("Author: ").append(getUsername()).append("\n")
				.append("New version: ").append(newDependantVersion).append("\n");

		if (COMPLETED.equals(codeSystemUpgradeJob.getStatus())) {
			result.append("Status: ").append(codeSystemUpgradeJob.getStatus());
			if (!isIntegrityCheckEmpty(codeSystem.getBranchPath())) {
				result.append(" with integrity failures");
			}
			result.append("\n");
		} else {
			result.append("Status: ").append(codeSystemUpgradeJob.getStatus()).append("\n");
		}

		if (FAILED.equals(codeSystemUpgradeJob.getStatus()) && StringUtils.hasLength(codeSystemUpgradeJob.getErrorMessage())) {
			result.append("Failure message: ").append(codeSystemUpgradeJob.getErrorMessage());
		}

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
}
