package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.entity.RVFFailureJiraAssociation;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.repository.RVFFailureJiraAssociationRepository;
import org.ihtsdo.authoringservices.service.client.RVFClientFactory;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@Transactional
public class RVFFailureJiraAssociationService {

	private static Logger logger = LoggerFactory.getLogger(RVFFailureJiraAssociationService.class);

	private static final int JIRA_SUMMARY_MAX_LENGTH = 255;

	@Value("${rvf.jira.url}")
	private String jiraUrl;

	private static final String DEFAULT_ISSUE_TYPE = "Service Request";

	private static final String DEFAULT_INT_PROJECT = "INFRA";

	private static final String DEFAULT_MANAGED_SERVICE_PROJECT = "MSSP";

	private static final String ENABLE_RVF_TICKET_GENERATION = "enableRvfTicketGeneration";

	@Value("${rvf.jira.ticket.watcher}")
	private String watcher;

	@Autowired
	private RVFFailureJiraAssociationRepository repository;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	@Qualifier("validationTicketOAuthJiraClient")
	private ImpersonatingJiraClientFactory jiraClientFactory;

	@Autowired
	private RVFClientFactory rvfClientFactory;

	public List<RVFFailureJiraAssociation> findByReportRunId(Long reportRunId) {
		return repository.findByReportRunId(reportRunId);
	}

	public Map<String, Object> createFailureJiraAssociations(String branchPath, Long reportRunId, String[] assertionIds) throws BusinessServiceException, IOException, JiraException {
		if (assertionIds.length == 0) {
			throw new IllegalArgumentException("Assertion IDs must not be empty");
		}

		validateBranchPathOrThrow(branchPath);
		Validation validation = getValidationOrThrow(branchPath);
		String rvfUrl = getRvfUrlOrThrow(reportRunId, validation);
		ValidationReport report = getValidationReportOrThrow(rvfUrl);

		final SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
		List<CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();
		boolean isManagedServiceBranch = isManagedServiceBranch(codeSystems, branchPath);
		String productName = getProductName(codeSystems, validation, branchPath);

		// check duplicate
		List<String> validAssertionIds = new ArrayList<>();
		List<RVFFailureJiraAssociation> dupplicatedAssocs = new ArrayList<>();
		List<RVFFailureJiraAssociation> existingAssocs = findByReportRunId(reportRunId);
		if (!existingAssocs.isEmpty()) {
			for (String assertionId : assertionIds) {
				RVFFailureJiraAssociation existingAssoc = existingAssocs.stream().filter(item -> item.getAssertionId().equals(assertionId)).findFirst().orElse(null);
				if (existingAssoc == null) {
					validAssertionIds.add(assertionId);
				} else {
					dupplicatedAssocs.add(existingAssoc);
				}
			}
		} else {
			validAssertionIds = Arrays.asList(assertionIds);
		}

		List<RVFFailureJiraAssociation> newAssociations = new ArrayList<>();
		List<ValidationReport.RvfValidationResult.TestResult.TestRunItem> assertionsFailed = getFailedAssertions(report);
		List<String> errors = new ArrayList<>();
		for (String assertionId : validAssertionIds) {
			ValidationReport.RvfValidationResult.TestResult.TestRunItem found = assertionsFailed.stream().filter(item -> item.getAssertionUuid() != null && item.getAssertionUuid().equals(assertionId)).findAny().orElse(null);
			if (found != null) {
				Issue jiraIssue = createJiraIssue(isManagedServiceBranch, generateSummary(found, productName), generateDescription(found, rvfUrl));
				Issue.NewAttachment[] attachments = new Issue.NewAttachment[1];
				attachments[0] = new Issue.NewAttachment(found.getAssertionUuid() + ".json", parsePrettyString(found.toString()).getBytes());
				jiraIssue.addAttachments(attachments);

				final RVFFailureJiraAssociation association = new RVFFailureJiraAssociation(reportRunId,found.getAssertionUuid(), jiraUrl + "browse/" + jiraIssue.getKey());
				repository.save(association);
				newAssociations.add(association);
			} else {
				errors.add(String.format("No failure found for the assertion %s", assertionId));
			}
		}
		Map<String, Object> result = new HashMap<>();
		result.put("newFailureJiraAssociations", newAssociations);
		result.put("duplicatedFailureJiraAssociations", dupplicatedAssocs);
		result.put("errors", errors);

		return result;
	}

	private String getRvfUrlOrThrow(Long reportRunId, Validation validation) throws BusinessServiceException {
		if (validation.getReportUrl() != null && validation.getReportUrl().contains(reportRunId.toString())) {
			return validation.getReportUrl();
		} else if (validation.getDailyBuildReportUrl() != null && validation.getDailyBuildReportUrl().contains(reportRunId.toString())) {
			return validation.getDailyBuildReportUrl();
		} else {
			throw new BusinessServiceException("No RVF URL found for Id " + reportRunId + " on branch " + validation.getBranchPath());
		}
	}

	private Validation getValidationOrThrow(String branchPath) throws BusinessServiceException {
		Validation validation;
		try {
			validation = validationService.getValidation(branchPath);
			if (validation == null) {
				throw new BusinessServiceException("No validation found on branch " + branchPath);
			}
		} catch (ExecutionException e) {
			throw new BusinessServiceException(e);
		}
		return validation;
	}

	private void validateBranchPathOrThrow(String branchPath) throws BusinessServiceException {
		Branch branch;
		try {
			branch = branchService.getBranchOrNull(branchPath);
			if (branch == null) {
				throw new BusinessServiceException("Branch " + branchPath + " not found");
			}
			final Map<String, Object> branchMetadata = branchService.getBranchMetadataIncludeInherited(branchPath);
			if (!branchMetadata.containsKey(ENABLE_RVF_TICKET_GENERATION) || !"true".equalsIgnoreCase((String) branchMetadata.get(ENABLE_RVF_TICKET_GENERATION))) {
				throw new BusinessServiceException("It's not allow to raise tickets on branch " + branchPath + ". Please check branch metadata");
			}

		} catch (ServiceException e) {
			throw new BusinessServiceException(e);
		}
	}

	private String getProductName(List<CodeSystem> codeSystems, Validation validation , String branchPath) {
		for (CodeSystem cs : codeSystems) {
			if (cs.getBranchPath().equals(branchPath)) {
				return cs.getShortName();
			}
		}

		if (validation != null && validation.getProjectKey() != null) {
			if (validation.getTaskKey() != null) {
				return validation.getTaskKey();
			}
			return validation.getProjectKey();
		}

		return branchPath;
	}

	private String generateSummary(ValidationReport.RvfValidationResult.TestResult.TestRunItem testRunItem, String productName) {
		String summary = productName  + ", " + testRunItem.getAssertionText().trim().replace("\\.+$", "") + ", " + testRunItem.getTestType().replace("DROOL_RULES", "DROOLS") + ", " + testRunItem.getAssertionUuid();
		if (summary.length() > JIRA_SUMMARY_MAX_LENGTH) {
			summary = summary.substring(0, JIRA_SUMMARY_MAX_LENGTH - 1);
		}
		return summary;
	}

	private String generateDescription(ValidationReport.RvfValidationResult.TestResult.TestRunItem testRunItem, String reportUrl) {
		StringBuilder result = new StringBuilder();
		result.append(testRunItem.getAssertionText()).append("\n")
				.append("Total number of failures: ").append(testRunItem.getFailureCount()).append("\n")
				.append("Report URL: ").append("[").append(reportUrl).append("|").append(reportUrl).append("]").append("\n");
		if (testRunItem.getFirstNInstances() != null) {
			List<ValidationReport.RvfValidationResult.TestResult.TestRunItem.FailureDetail> firstNInstances = getFirstNInstances(testRunItem.getFirstNInstances());
			result.append("First ").append(firstNInstances.size()).append(" failures: \n");
			for (ValidationReport.RvfValidationResult.TestResult.TestRunItem.FailureDetail failureDetail: firstNInstances) {
				result.append("* ").append(failureDetail.toString()).append("\n");
			}
		}

		return result.toString();
	}

	private String parsePrettyString(String input) {
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		JsonElement je = JsonParser.parseString(input);
		return gson.toJson(je);
	}

	private List<ValidationReport.RvfValidationResult.TestResult.TestRunItem.FailureDetail> getFirstNInstances(List<ValidationReport.RvfValidationResult.TestResult.TestRunItem.FailureDetail> instances) {
		int firstNCount = Math.min(10, instances.size());

		return instances.subList(0, firstNCount);
	}

	private List<ValidationReport.RvfValidationResult.TestResult.TestRunItem> getFailedAssertions(ValidationReport report) {
		if (report.getRvfValidationResult() != null && report.getRvfValidationResult().getTestResult() != null) {
			if (report.getRvfValidationResult().getTestResult().getAssertionsFailed() != null) {
				return report.getRvfValidationResult().getTestResult().getAssertionsFailed();
			}
		}
		return Collections.emptyList();
	}

	private Issue createJiraIssue(boolean isManagedServiceBranch, String summary, String description) throws BusinessServiceException {
		Issue jiraIssue;
		try {
			jiraIssue = getJiraClient().createIssue(isManagedServiceBranch ? DEFAULT_MANAGED_SERVICE_PROJECT : DEFAULT_INT_PROJECT, DEFAULT_ISSUE_TYPE)
					.field(Field.SUMMARY, summary)
					.field(Field.DESCRIPTION, description)
					.execute();

			if (!isManagedServiceBranch && !watcher.equalsIgnoreCase(getUsername())) {
				jiraIssue.addWatcher(watcher);
				jiraIssue.refresh();
			}

			final Issue.FluentUpdate updateRequest = jiraIssue.update();
			updateRequest.field(Field.ASSIGNEE, "");

			updateRequest.execute();
		} catch (JiraException e) {
			logger.error(e.getMessage());
			throw new BusinessServiceException("Failed to create Jira task. Error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
		}

		return jiraIssue;
	}

	private boolean isManagedServiceBranch(List<CodeSystem> codeSystems, String branchPath) {
		for (CodeSystem cs : codeSystems) {
			if (branchPath.startsWith("MAIN/SNOMEDCT-")
					&& cs.getBranchPath().startsWith("MAIN/SNOMEDCT-")
					&& branchPath.startsWith(cs.getBranchPath())) {
				return cs.getMaintainerType() != null && cs.getMaintainerType().equals("Managed Service");
			}
		}

		return false;
	}

	private ValidationReport getValidationReportOrThrow(String url) throws IOException, BusinessServiceException {
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().failOnUnknownProperties(false).build();
		String validationReportString = rvfClientFactory.getClient().getValidationReport(url);
		if (StringUtils.hasLength(validationReportString)) {
			validationReportString = validationReportString.replace("\"TestResult\"", "\"testResult\"");
		} else {
			throw new BusinessServiceException("No RVF report found for " + url);
		}

		ValidationReport report = objectMapper.readValue(validationReportString, ValidationReport.class);
		if (!report.isComplete()) {
			throw new BusinessServiceException("RVF report must be completed");
		}

		return report;
	}

	private JiraClient getJiraClient() {
		return jiraClientFactory.getImpersonatingInstance(getUsername());
	}

	private String getUsername() {
		return SecurityUtil.getUsername();
	}

	private static final class ValidationReport {

		public static final String COMPLETE = "COMPLETE";

		private String status;

		private RvfValidationResult rvfValidationResult;

		public boolean isComplete() {
			return COMPLETE.equals(status);
		}

		public String getStatus() {
			return status;
		}

		public RvfValidationResult getRvfValidationResult() {
			return rvfValidationResult;
		}

		private static final class RvfValidationResult {

			private TestResult testResult;

			public TestResult getTestResult() {
				return testResult;
			}

			private static final class TestResult {
				private List<TestRunItem> assertionsFailed;

				private List<TestRunItem> assertionsWarning;

				public List<TestRunItem> getAssertionsFailed() {
					return assertionsFailed;
				}

				public List<TestRunItem> getAssertionsWarning() {
					return assertionsWarning;
				}

				private static final class TestRunItem {
					private String testType;
					private String assertionUuid;
					private String assertionText;
					private String severity;
					private Long failureCount;
					private String failureMessage;
					private List<FailureDetail> firstNInstances;

					public String getTestType() {
						return testType;
					}

					public String getAssertionUuid() {
						return assertionUuid;
					}

					public String getAssertionText() {
						return assertionText;
					}

					public String getSeverity() {
						return severity;
					}

					public Long getFailureCount() {
						return failureCount;
					}

					public String getFailureMessage() {
						return failureMessage;
					}

					public List<FailureDetail> getFirstNInstances() {
						return firstNInstances;
					}

					@Override
					public String toString() {
						return "{" +
								"\"testType\": \"" + testType + '\"' +
								", \"assertionUuid\": \"" + assertionUuid + '\"' +
								", \"assertionText\": \"" + assertionText + '\"' +
								", \"severity\": " + (severity != null ? "\"" + severity + "\"" : null) +
								", \"failureCount\": " + failureCount +
								", \"failureMessage\": " + (failureMessage != null ? "\"" + failureMessage + "\"" : null) +
								", \"firstNInstances\": " + firstNInstances +
								'}';
					}

					private static final class FailureDetail {
						private String conceptId;
						private String conceptFsn;
						private String detail;
						private String componentId;
						private String fullComponent;

						public String getConceptId() {
							return conceptId;
						}

						public String getConceptFsn() {
							return conceptFsn;
						}

						public String getDetail() {
							return detail;
						}

						public String getComponentId() {
							return componentId;
						}

						public String getFullComponent() {
							return fullComponent;
						}

						@Override
						public String toString() {
							return "{\n\t" +
									"\"conceptId\": " + (conceptId != null ? '\"' + conceptId + '\"' : null) + ",\n\t" +
									"\"conceptFsn\": " + (conceptFsn != null ? '\"' + conceptFsn + '\"' : null) + ",\n\t" +
									"\"detail\": " + (detail != null ? '\"' + detail + '\"' : null) + ",\n\t" +
									"\"componentId\": " + (componentId != null ? '\"' + componentId + '\"' : null) + ",\n\t" +
									"\"fullComponent\": " + (fullComponent != null ? '\"' + fullComponent + '\"' : null) + "\n" +
									"}";
						}
					}
				}
			}
		}
	}
}
