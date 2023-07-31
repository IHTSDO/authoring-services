package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.rcarz.jiraclient.Status;
import net.rcarz.jiraclient.User;
import net.rcarz.jiraclient.*;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.domain.TaskMessagesDetail;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
import org.ihtsdo.authoringservices.service.util.TimerUtil;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BadRequestException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jms.JMSException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TaskService {

	public static final String JIRA_PROJECT_SUFFIX = "-1";
	private static final String ENABLED_TEXT = "Enabled";

	private static final String DISABLED_TEXT = "Disabled";

	public static final String FAILED_TO_RETRIEVE = "Failed-to-retrieve";
	
	private static final String INCLUDE_ALL_FIELDS = "*all";
	private static final String EXCLUDE_STATUSES = " AND (status != \"" + TaskStatus.COMPLETED.getLabel()
			+ "\" AND status != \"" + TaskStatus.DELETED.getLabel() + "\") ";
	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";
	private static final int LIMIT_UNLIMITED = -1;

	@Autowired
	private BranchService branchService;

	@Autowired
	private SnowstormClassificationClient classificationService;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private UiStateService uiService;

	@Autowired
	private InstanceConfiguration instanceConfiguration;

	@Autowired
	private MessagingHelper messagingHelper;

	@Autowired
	private EmailService emailService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private PromotionService promotionService;

	@Value("${task-state-change.notification-queues}")
	private Set<String> taskStateChangeNotificationQueues;

	private final ImpersonatingJiraClientFactory jiraClientFactory;
	private final String jiraExtensionBaseField;
	private final String jiraProductCodeField;
	private final String jiraProjectPromotionField;
	private final String jiraProjectLockedField;
	private final String jiraTaskPromotionField;
	private final String jiraProjectRebaseField;
	private final String jiraProjectScheduledRebaseField;
	private final String jiraProjectMrcmField;
	private final String jiraCrsIdField;
	private final String jiraOrganizationField;
	private final String jiraProjectTemplatesField;
	private final String jiraProjectSpellCheckField;
	private final Set<String> projectJiraFetchFields;
	private final Set<String> myTasksRequiredFields;

	private LoadingCache<String, ProjectDetails> projectDetailsCache;
	private final ExecutorService executorService;
	private static final String UNIT_TEST = "UNIT_TEST";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TaskService(@Autowired @Qualifier("authoringTaskOAuthJiraClient") ImpersonatingJiraClientFactory jiraClientFactory, @Value("${jira.username}") String jiraUsername) throws JiraException {
		this.jiraClientFactory = jiraClientFactory;
		executorService = Executors.newCachedThreadPool();

		if (!jiraUsername.equals(UNIT_TEST)) {
			logger.info("Fetching Jira custom field names.");
			final JiraClient jiraClientForFieldLookup = jiraClientFactory.getAdminInstance();
			AuthoringTask.setJiraReviewerField(JiraHelper.fieldIdLookup("Reviewer", jiraClientForFieldLookup, null));
			AuthoringTask.setJiraReviewersField(JiraHelper.fieldIdLookup("Reviewers", jiraClientForFieldLookup, null));
			projectJiraFetchFields = new HashSet<>();
			projectJiraFetchFields.add("project");
			jiraExtensionBaseField = JiraHelper.fieldIdLookup("Extension Base", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProductCodeField = JiraHelper.fieldIdLookup("Product Code", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectPromotionField = JiraHelper.fieldIdLookup("SCA Project Promotion", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectLockedField = JiraHelper.fieldIdLookup("SCA Project Locked", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraTaskPromotionField = JiraHelper.fieldIdLookup("SCA Task Promotion", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectRebaseField = JiraHelper.fieldIdLookup("SCA Project Rebase", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectScheduledRebaseField = JiraHelper.fieldIdLookup("SCA Project Scheduled Rebase", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectMrcmField = JiraHelper.fieldIdLookup("SCA Project MRCM", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraCrsIdField = JiraHelper.fieldIdLookup("CRS-ID", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraOrganizationField = JiraHelper.fieldIdLookup("Organization", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectTemplatesField = JiraHelper.fieldIdLookup("SCA Project Templates", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectSpellCheckField = JiraHelper.fieldIdLookup("SCA Project Spell Check", jiraClientForFieldLookup, projectJiraFetchFields);
			logger.info("Jira custom field names fetched. (e.g. {}).", jiraExtensionBaseField);
		
			init();
		} else {
			projectJiraFetchFields = null;
			jiraExtensionBaseField = null;
			jiraProductCodeField = null;
			jiraProjectPromotionField = null;
			jiraProjectLockedField = null;
			jiraTaskPromotionField = null;
			jiraProjectRebaseField = null;
			jiraProjectScheduledRebaseField = null;
			jiraProjectMrcmField = null;
			jiraCrsIdField = null;
			jiraOrganizationField = null;
			jiraProjectTemplatesField = null;
			jiraProjectSpellCheckField = null;
		}

		myTasksRequiredFields = Sets.newHashSet(
				Field.PROJECT,
				Field.SUMMARY,
				Field.STATUS,
				Field.DESCRIPTION,
				Field.ASSIGNEE,
				Field.CREATED_DATE,
				Field.UPDATED_DATE,
				Field.LABELS,
				AuthoringTask.jiraReviewerField,
				AuthoringTask.jiraReviewersField);
	}

	public void init() {
		projectDetailsCache = CacheBuilder.newBuilder().maximumSize(10000)
				.build(new CacheLoader<>() {
                    @Override
                    public ProjectDetails load(String projectKey) throws BusinessServiceException {
                        final Issue projectTicket = getProjectTicket(projectKey);
                        return getProjectDetailsPopulatingCache(projectTicket);
                    }

                    @Override
                    public Map<String, ProjectDetails> loadAll(Iterable<? extends String> keys) throws Exception {
                        final Map<String, Issue> projectTickets = getProjectTickets(keys);
                        Map<String, ProjectDetails> keyToBaseMap = new HashMap<>();
                        for (String projectKey : projectTickets.keySet()) {
                            keyToBaseMap.put(projectKey,
                                    getProjectDetailsPopulatingCache(projectTickets.get(projectKey)));
                        }
                        return keyToBaseMap;
                    }
                });
	}

	@PostConstruct
	public void postConstruct() {
		logger.info("{} task state notification queues configured {}", taskStateChangeNotificationQueues.size(), taskStateChangeNotificationQueues);
	}

	public List<AuthoringProject> listProjects(Boolean lightweight) throws JiraException {
		final TimerUtil timer = new TimerUtil("ProjectsList");
		List<Issue> projectTickets = new ArrayList<>();
		// Search for authoring project tickets this user has visibility of
		List<Issue> issues = searchIssues("type = \"SCA Authoring Project\"", -1, projectJiraFetchFields);

		timer.checkpoint("First jira search");
		for (Issue projectMagicTicket : issues) {
			final String productCode = getProjectDetailsPopulatingCache(projectMagicTicket).getProductCode();
			if (instanceConfiguration.isJiraProjectVisible(productCode)) {
				projectTickets.add(projectMagicTicket);
			}
		}
		timer.checkpoint("Jira searches");
		final List<AuthoringProject> authoringProjects = buildAuthoringProjects(projectTickets, lightweight != null && lightweight);
		if (lightweight != null && lightweight) {
			timer.checkpoint("project path search only without validation and classification");
		} else {
			timer.checkpoint("validation and classification");
		}
		timer.finish();
		return authoringProjects;
	}

	public AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException {
		List<Issue> issues = Collections.singletonList(getProjectTicketOrThrow(projectKey));
		List<AuthoringProject> projects = buildAuthoringProjects(issues, false);
		if (projects.size() == 0) {
			throw new BusinessServiceException ("Failed to recover project: " + projectKey +". See earlier logs for reason");
		}
		return projects.get(0);
	}

	public AuthoringProject retrieveProject(String projectKey, boolean lightweight) throws BusinessServiceException {
		List<Issue> issues = Collections.singletonList(getProjectTicketOrThrow(projectKey));
		List<AuthoringProject> projects = buildAuthoringProjects(issues, lightweight);
		if (projects.size() == 0) {
			throw new BusinessServiceException ("Failed to recover project: " + projectKey +". See earlier logs for reason");
		}
		return projects.get(0);
	}

	public String getProjectBranchPathUsingCache(String projectKey) throws BusinessServiceException {
		return PathHelper.getProjectPath(getProjectBaseUsingCache(projectKey), projectKey);
	}

	public String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException {
		try {
			return projectDetailsCache.get(projectKey).getBaseBranchPath();
		} catch (ExecutionException e) {
			throw new BusinessServiceException("Failed to retrieve project path.", e);
		}
	}

	public void lockProject(String projectKey) throws BusinessServiceException {
		Issue issue;
		try {
			issue = getJiraClient().getIssue(projectKey + JIRA_PROJECT_SUFFIX);
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to retrieve JIRA issue with key " + projectKey, e);
		}
		if (issue != null) {
			try {
				JSONObject updateObj = new JSONObject();
				updateObj.put("value", ENABLED_TEXT);

				final Issue.FluentUpdate updateRequest = issue.update();
				updateRequest.field(jiraProjectLockedField, updateObj);

				updateRequest.execute();
			} catch (JiraException e) {
				logger.error("Failed to update issue with key " + (projectKey + JIRA_PROJECT_SUFFIX), e);
				throw new BusinessServiceException("Failed to update issue with key " + (projectKey + JIRA_PROJECT_SUFFIX));
			}
		}
	}

	public void unlockProject(String projectKey) throws BusinessServiceException {
		Issue issue;
		try {
			issue = getJiraClient().getIssue(projectKey + JIRA_PROJECT_SUFFIX);
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to retrieve JIRA issue with key " + projectKey, e);
		}
		if (issue != null) {
			try {
				JSONObject updateObj = new JSONObject();
				updateObj.put("value", DISABLED_TEXT);

				final Issue.FluentUpdate updateRequest = issue.update();
				updateRequest.field(jiraProjectLockedField, updateObj);

				updateRequest.execute();
			} catch (JiraException e) {
				logger.error("Failed to update issue with key " + (projectKey + JIRA_PROJECT_SUFFIX), e);
				throw new BusinessServiceException("Failed to update issue with key " + (projectKey + JIRA_PROJECT_SUFFIX), e);
			}
		}
	}

	private List<AuthoringProject> buildAuthoringProjects(List<Issue> projectTickets, boolean lightweight) {
		if (projectTickets.isEmpty()) {
			return new ArrayList<>();
		}
		final List<AuthoringProject> authoringProjects = new ArrayList<>();
		final Set<String> branchPaths = new HashSet<>();
		final Map<String, Branch> parentBranchCache = new ConcurrentHashMap<>();
		final SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
		List<CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();

		SecurityContext securityContext = SecurityContextHolder.getContext();

		JiraClient jiraClient = getJiraClient();
		Future<Map<String, JiraProject>> unfilteredProjects = executorService.submit(() -> getProjects(jiraClient.getRestClient()).stream().collect(Collectors.toMap(JiraProject::getKey, Function.identity())));

		projectTickets.parallelStream().forEach(projectTicket -> {
			SecurityContextHolder.setContext(securityContext);
			try {
				final String projectKey = projectTicket.getProject().getKey();
				final String extensionBase = getProjectDetailsPopulatingCache(projectTicket).getBaseBranchPath();
				final String branchPath = PathHelper.getProjectPath(extensionBase, projectKey);
				
				String latestClassificationJson = null;
				if (!lightweight) {
					latestClassificationJson = classificationService.getLatestClassification(branchPath);
				}
				
				final boolean promotionDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectPromotionField)));
				final boolean projectLocked = !DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectLockedField)));
				final boolean taskPromotionDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraTaskPromotionField)));
				final boolean rebaseDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectRebaseField)));
				final boolean scheduledRebaseDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectScheduledRebaseField)));
				final boolean mrcmDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectMrcmField)));
				final boolean templatesDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectTemplatesField)));
				final boolean spellCheckDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectSpellCheckField)));

				final Branch branchOrNull = branchService.getBranchOrNull(branchPath);
				String parentPath = PathHelper.getParentPath(branchPath);
				Branch parentBranchOrNull = parentBranchCache.get(parentPath);
				if (parentBranchOrNull == null) {
					parentBranchOrNull = branchService.getBranchOrNull(parentPath);
					if (parentBranchOrNull == null) {
						logger.error("Project {} expected parent branch does not exist: {}", projectKey, parentPath);
						return;
					}
					parentBranchCache.put(parentPath, parentBranchOrNull);
				}
				CodeSystem codeSystem = codeSystems.stream().filter(c -> parentPath.equals(c.getBranchPath())).findFirst().orElse(null);
				if (codeSystem == null && parentPath.contains("/")) {
					// Attempt match using branch grandfather
					String grandfatherPath = PathHelper.getParentPath(parentPath);
					codeSystem = codeSystems.stream().filter(c -> grandfatherPath.equals(c.getBranchPath())).findFirst().orElse(null);
				}

				String branchState = null;
				Map<String, Object> metadata = new HashMap<>();
				Long baseTimeStamp = null;
				Long headTimeStamp = null;
				if (branchOrNull != null) {
					branchState = branchOrNull.getState();
					baseTimeStamp = branchOrNull.getBaseTimestamp();
					headTimeStamp = branchOrNull.getHeadTimestamp();
					metadata.putAll(parentBranchOrNull.getMetadata());
					if (branchOrNull.getMetadata() != null) {
						metadata.putAll(branchOrNull.getMetadata());
					}
				}
				synchronized (branchPaths) {
					branchPaths.add(branchPath);
				}
				Map<String, JiraProject> projectMap = unfilteredProjects.get();
				JiraProject project = projectMap.get(projectKey);
				final AuthoringProject authoringProject = new AuthoringProject(projectKey, project.getName(),
						project.getLead(), branchPath, branchState, baseTimeStamp, headTimeStamp, latestClassificationJson, promotionDisabled, mrcmDisabled, templatesDisabled, spellCheckDisabled, rebaseDisabled, scheduledRebaseDisabled, taskPromotionDisabled, projectLocked);
				authoringProject.setMetadata(metadata);
				authoringProject.setCodeSystem(codeSystem);
				synchronized (authoringProjects) {
					authoringProjects.add(authoringProject);
				}
			} catch (RestClientException | ServiceException | InterruptedException | ExecutionException e) {
				logger.error("Failed to fetch details of project {}", projectTicket.getProject().getName(), e);
			}
		});

		if (!lightweight) {
			final ImmutableMap<String, Validation> validationMap;
			try {
				validationMap = validationService.getValidations(branchPaths);
				for (AuthoringProject authoringProject : authoringProjects) {
					try {
						String branchPath = authoringProject.getBranchPath();
						Validation validation = validationMap.get(branchPath);
						if (ValidationJobStatus.COMPLETED.name().equals(validation.getStatus())
								&& validation.getContentHeadTimestamp() != null
								&& authoringProject.getBranchHeadTimestamp() != null
								&& !authoringProject.getBranchHeadTimestamp().equals(validation.getContentHeadTimestamp())) {
							authoringProject.setValidationStatus(ValidationJobStatus.STALE.name());
						} else {
							authoringProject.setValidationStatus(validation.getStatus());
						}
					} catch (Exception e) {
						logger.error("Failed to recover/set validation status for " + authoringProject.getKey(), e);
					}
				}
			} catch (ExecutionException e) {
				logger.warn("Failed to fetch validation statuses for branch paths {}", branchPaths);
			}
		}
		return authoringProjects;
	}

	private ProjectDetails getProjectDetailsPopulatingCache(Issue projectMagicTicket) {
		final String base = Strings
				.nullToEmpty(JiraHelper.toStringOrNull(projectMagicTicket.getField(jiraExtensionBaseField)));
		final String productCode = Strings
				.nullToEmpty(JiraHelper.toStringOrNull(projectMagicTicket.getField(jiraProductCodeField)));
		// Update cache with recently fetched project base value
		final ProjectDetails details = new ProjectDetails(base, productCode);
		projectDetailsCache.put(projectMagicTicket.getProject().getKey(), details);
		return details;
	}

	public AuthoringMain retrieveMain() throws BusinessServiceException {
		return buildAuthoringMain();
	}

	private AuthoringMain buildAuthoringMain() throws BusinessServiceException {
		try {
			String path = PathHelper.getProjectPath(null, null);
			Collection<String> paths = Collections.singletonList(path);
			final ImmutableMap<String, Validation> validationMap = validationService.getValidations(paths);
			final String branchState = branchService.getBranchStateOrNull(PathHelper.getMainPath());
			final String latestClassificationJson = classificationService
					.getLatestClassification(PathHelper.getMainPath());
			return new AuthoringMain(path, branchState, validationMap.get(path).getStatus(), latestClassificationJson);
		} catch (ExecutionException | RestClientException | ServiceException e) {
			throw new BusinessServiceException("Failed to retrieve Main", e);
		}
	}

	public Issue getProjectTicketOrThrow(String projectKey) {
		Issue issue = null;
		try {
			issue = getProjectTicket(projectKey);
		} catch (BusinessServiceException e) {
			logger.error("Failed to load project ticket {}", projectKey, e);
		}
		if (issue == null) {
			throw new IllegalArgumentException("Authoring project with key " + projectKey + " is not accessible.");
		}
		return issue;
	}

	public Issue getProjectTicket(String projectKey) throws BusinessServiceException {
		return getProjectTickets(Collections.singleton(projectKey)).get(projectKey);
	}

	private Map<String, Issue> getProjectTickets(Iterable<? extends String> projectKeys)
			throws BusinessServiceException {
		StringBuilder magicTicketQuery = new StringBuilder();
		magicTicketQuery.append("(");
		for (String projectKey : projectKeys) {
			if (magicTicketQuery.length() > 1) {
				magicTicketQuery.append(" OR ");
			}
			magicTicketQuery.append("project = ").append(projectKey);
		}
		magicTicketQuery.append(") AND type = \"SCA Authoring Project\"");

		try {
			final List<Issue> issues = getJiraClient().searchIssues(magicTicketQuery.toString()).issues;
			Map<String, Issue> issueMap = new HashMap<>();
			for (Issue issue : issues) {
				issueMap.put(issue.getProject().getKey(), issue);
			}
			return issueMap;
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to load Projects from Jira.", e);
		}
	}

	public List<Issue> getTaskIssues(String projectKey, TaskStatus taskStatus) throws BusinessServiceException {
		try {
			return getJiraClient().searchIssues(getProjectTaskJQL(projectKey, taskStatus), -1).issues;
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to load tasks in project '" + projectKey + "' from Jira.", e);
		}
	}

	public List<AuthoringTask> listTasks(String projectKey, Boolean lightweight) throws BusinessServiceException {
		getProjectOrThrow(projectKey);
		List<Issue> issues;
		try {
			issues = searchIssues(getProjectTaskJQL(projectKey, null), LIMIT_UNLIMITED);
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to list tasks.", e);
		}
		return buildAuthoringTasks(issues, lightweight);
	}

	public AuthoringTask retrieveTask(String projectKey, String taskKey, Boolean lightweight) throws BusinessServiceException {
		try {
			Issue issue = getIssue(taskKey);
			final List<AuthoringTask> authoringTasks = buildAuthoringTasks(Collections.singletonList(issue), lightweight);
			return !authoringTasks.isEmpty() ? authoringTasks.get(0) : null;
		} catch (JiraException e) {
			if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 404) {
				throw new ResourceNotFoundException("Task not found " + toString(projectKey, taskKey), e);
			}
			throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
		}
	}

	public String getTaskBranchPathUsingCache(String projectKey, String taskKey) throws BusinessServiceException {
		return PathHelper.getTaskPath(getProjectBaseUsingCache(projectKey), projectKey, taskKey);
	}

	public String getBranchPathUsingCache(String projectKey, String taskKey) throws BusinessServiceException {
		if (!Strings.isNullOrEmpty(projectKey)) {
			final String extensionBase = getProjectBaseUsingCache(projectKey);
			if (!Strings.isNullOrEmpty(taskKey)) {
				return PathHelper.getTaskPath(extensionBase, projectKey, taskKey);
			}
			return PathHelper.getProjectPath(extensionBase, projectKey);
		}
		return null;
	}

	private Issue getIssue(String taskKey) throws JiraException {
		return getIssue(taskKey, false);
	}

	private Issue getIssue(String taskKey, boolean includeAll) throws JiraException {
		// If we don't need all fields, then the existing implementation is
		// sufficient
		if (includeAll) {
			return getJiraClient().getIssue(taskKey, INCLUDE_ALL_FIELDS, "changelog");
		} else {
			return getJiraClient().getIssue(taskKey, INCLUDE_ALL_FIELDS);
		}
	}

	/**
	 * @param jql
	 * @param limit
	 *            maximum number of issues to return. If -1 the results are
	 *            unlimited.
	 * @return
	 * @throws JiraException
	 */
	private List<Issue> searchIssues(String jql, int limit) throws JiraException {
		return searchIssues(jql, limit, null);
	}
	private List<Issue> searchIssues(String jql, int limit, Set<String> requiredFields) throws JiraException {
		List<Issue> issues = new ArrayList<>();
		Issue.SearchResult searchResult;
		String requiredFieldParam = null;
		if (requiredFields != null && !requiredFields.isEmpty()) {
			requiredFieldParam = String.join(",", requiredFields);
		}
		do {
			searchResult = getJiraClient().searchIssues(jql, requiredFieldParam, limit - issues.size(), issues.size());
			issues.addAll(searchResult.issues);
		} while (searchResult.total > issues.size() && (limit == LIMIT_UNLIMITED || issues.size() < limit));

		return issues;

	}

	/**
	 * Search issues based on Jira Summary or ID field
	 */
	public List<AuthoringTask> searchTasks(String criteria, Boolean lightweight) throws JiraException, BusinessServiceException {
		if (StringUtils.isEmpty(criteria)) {
			return Collections.emptyList();
		}
		String[] arrayStr = criteria.split("-");
		boolean isJiraId = arrayStr.length == 2 && NumberUtils.isNumber(arrayStr[1]);
		String jql = "type = \"" + AUTHORING_TASK_TYPE + "\"" + " AND status != \"" + TaskStatus.DELETED.getLabel() + "\"" + (isJiraId ? " AND id = \"" + criteria + "\"" : " AND summary ~ \"" + criteria + "\"");
		List<Issue> issues;
		try {
			issues = searchIssues(jql, LIMIT_UNLIMITED, myTasksRequiredFields);
		} catch (JiraException exception) {
			if (isJiraId) {
				return Collections.emptyList();
			} else {
				throw exception;
			}
		}
		return buildAuthoringTasks(issues, lightweight != null && lightweight);
	}

	public List<AuthoringTask> listMyTasks(String username, String excludePromoted) throws JiraException, BusinessServiceException {
		String jql = "assignee = \"" + username + "\" AND type = \"" + AUTHORING_TASK_TYPE + "\" " + EXCLUDE_STATUSES;
		if (null != excludePromoted && excludePromoted.equalsIgnoreCase("TRUE")) {
			jql += " AND status != \"Promoted\"";
		}
		List<Issue> issues = searchIssues(jql, LIMIT_UNLIMITED, myTasksRequiredFields);
		return buildAuthoringTasks(issues, false);
	}

	public List<AuthoringTask> listMyOrUnassignedReviewTasks(String excludePromoted) throws JiraException, BusinessServiceException {
		String jql = "type = \"" + AUTHORING_TASK_TYPE + "\" " + "AND assignee != currentUser() AND "
				+ "(((Reviewer = null AND Reviewers = null) AND status = \"" + TaskStatus.IN_REVIEW.getLabel() + "\") OR ((Reviewer = currentUser() OR Reviewers = currentUser()) AND ";
		if (null != excludePromoted && excludePromoted.equalsIgnoreCase("TRUE")) {
			jql += "(status = \"" + TaskStatus.IN_REVIEW.getLabel() + "\" OR status = \"" + TaskStatus.REVIEW_COMPLETED.getLabel() + "\")))";
		} else {
			jql += "(status = \"" + TaskStatus.IN_REVIEW.getLabel() + "\" OR status = \"" + TaskStatus.REVIEW_COMPLETED.getLabel() + "\" OR status = \"" + TaskStatus.PROMOTED.getLabel() + "\")))";
		}
		List<Issue> issues = searchIssues(jql, LIMIT_UNLIMITED);
		return buildAuthoringTasks(issues, false);
	}

	private String getProjectTaskJQL(String projectKey, TaskStatus taskStatus) {
		String jql = "project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\" " + EXCLUDE_STATUSES;
		if (taskStatus != null) {
			jql += " AND status = \"" + taskStatus.getLabel() + "\"";
		}
		return jql;
	}

	public AuthoringTask createTask(String projectKey, String username, AuthoringTaskCreateRequest taskCreateRequest)
			throws BusinessServiceException {
		Issue jiraIssue;
		try {
			jiraIssue = getJiraClient().createIssue(projectKey, AUTHORING_TASK_TYPE)
					.field(Field.SUMMARY, taskCreateRequest.getSummary())
					.field(Field.DESCRIPTION, taskCreateRequest.getDescription()).field(Field.ASSIGNEE, username)
					.execute();
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to create Jira task", e);
		}

		AuthoringTask authoringTask = new AuthoringTask(jiraIssue, getProjectBaseUsingCache(projectKey));
		try {
			branchService.createProjectBranchIfNeeded(PathHelper.getParentPath(authoringTask.getBranchPath()));
		} catch (ServiceException e) {
			throw new BusinessServiceException("Failed to create project branch.", e);
		}
		// Task branch creation is delayed until the user starts work to prevent
		// having to rebase straight away.
		return authoringTask;
	}

	public void clearClassificationCache(String branchPath) {
		classificationService.evictClassificationCache(branchPath);
	}

	public AuthoringTask createTask(String projectKey, AuthoringTaskCreateRequest taskCreateRequest)
			throws BusinessServiceException {
		return createTask(projectKey, getUsername(), taskCreateRequest);
	}

	private List<AuthoringTask> buildAuthoringTasks(List<Issue> tasks, Boolean lightweight) throws BusinessServiceException {
		final TimerUtil timer = new TimerUtil("BuildTaskList", Level.DEBUG);
		final String username = getUsername();
		List<AuthoringTask> allTasks = new ArrayList<>();
		try {
			// Map of task paths to tasks
			Map<String, AuthoringTask> startedTasks = new HashMap<>();

			Set<String> projectKeys = new HashSet<>();
			for (Issue issue : tasks) {
				projectKeys.add(issue.getProject().getKey());
			}

			final Map<String, ProjectDetails> projectKeyToBranchBaseMap = projectDetailsCache.getAll(projectKeys);
			for (Issue issue : tasks) {
				final ProjectDetails projectDetails = projectKeyToBranchBaseMap.get(issue.getProject().getKey());
				if (instanceConfiguration.isJiraProjectVisible(projectDetails.getProductCode())) {
					AuthoringTask task = new AuthoringTask(issue, projectDetails.getBaseBranchPath());

					ProcessStatus autoPromotionStatus = promotionService.getAutomateTaskPromotionStatus(task.getProjectKey(), task.getKey());
					if (autoPromotionStatus != null) {
						switch (autoPromotionStatus.getStatus()) {
							case "Queued":
								task.setStatus(TaskStatus.AUTOMATED_PROMOTION_QUEUED);
								break;
							case "Rebasing":
								task.setStatus(TaskStatus.AUTOMATED_PROMOTION_REBASING);
								break;
							case "Classifying":
								task.setStatus(TaskStatus.AUTOMATED_PROMOTION_CLASSIFYING);
								break;
							case "Promoting":
								task.setStatus(TaskStatus.AUTOMATED_PROMOTION_PROMOTING);
								break;
						}
					}

					allTasks.add(task);
					// Fetch the extra statuses for tasks that are not new and have a branch
					if (task.getStatus() != TaskStatus.NEW) {
						Branch branch = branchService.getBranchOrNull(task.getBranchPath());
						timer.checkpoint("Recovered branch state");
						if (branch != null) {
							task.setBranchState(branch.getState());
							task.setBranchBaseTimestamp(branch.getBaseTimestamp());
							task.setBranchHeadTimestamp(branch.getHeadTimestamp());
							
							if (lightweight == null || !lightweight) {
								task.setLatestClassificationJson(classificationService.getLatestClassification(task.getBranchPath()));
								timer.checkpoint("Recovered classification");
								// get the review message details and append to task
								TaskMessagesDetail detail = reviewService.getTaskMessagesDetail(task.getProjectKey(), task.getKey(),
										username);
								task.setFeedbackMessagesStatus(detail.getTaskMessagesStatus());
								task.setFeedbackMessageDate(detail.getLastMessageDate());
								task.setViewDate(detail.getViewDate());
								timer.checkpoint("Recovered feedback messages");
							}
							startedTasks.put(task.getBranchPath(), task);
						}
					}
				}
			}

			if (lightweight == null || !lightweight) {
				// Fetch validation statuses in bulk
				if (!startedTasks.isEmpty()) {
					Set<String> paths = startedTasks.keySet();
					final ImmutableMap<String, Validation> validationMap = validationService.getValidations(paths);
					timer.checkpoint("Recovered " + (validationMap == null ? "null" : validationMap.size()) + " ValidationStatuses");

					if (validationMap == null || validationMap.isEmpty()) {
						logger.warn("Failed to recover validation statuses for {} branches including '{}'.", paths.size(), paths.iterator().next());
					} else {
						for (final String path : paths) {
							Validation validation = validationMap.get(path);
							if (ValidationJobStatus.COMPLETED.name().equals(validation.getStatus())
								&& validation.getContentHeadTimestamp() != null
								&& !startedTasks.get(path).getBranchHeadTimestamp().equals(validation.getContentHeadTimestamp())) {
								startedTasks.get(path).setLatestValidationStatus(ValidationJobStatus.STALE.name());
							} else {
								startedTasks.get(path).setLatestValidationStatus(validation.getStatus());
							}
						}
					}
				}
			}
			timer.finish();
		} catch (ExecutionException | RestClientException | ServiceException e) {
			throw new BusinessServiceException("Failed to retrieve task list.", e);
		}
		return allTasks;
	}

	private void getProjectOrThrow(String projectKey) {
		try {
			getJiraClient().getProject(projectKey);
		} catch (JiraException e) {
			throw new ResourceNotFoundException("Project", projectKey);
		}
	}

	public void addCommentLogErrors(String projectKey, String commentString) {
		final Issue projectTicket = getProjectTicketOrThrow(projectKey);
		addCommentLogErrors(projectKey, projectTicket.getKey(), commentString);
	}

	public void addCommentLogErrors(String projectKey, String taskKey, String commentString) {
		try {
			addComment(taskKey, commentString);
		} catch (JiraException e) {
			logger.error("Failed to set message on jira ticket {}/{}: {}", projectKey, taskKey, commentString, e);
		}
	}

	public void addComment(String taskKey, String commentString) throws JiraException {
		Issue issue = getIssue(taskKey);
		issue.addComment(commentString);
		issue.update(); // Pick up new comment locally too
	}

	private JiraClient getJiraClient() {
		return jiraClientFactory.getImpersonatingInstance(getUsername());
	}

	private String getUsername() {
		return SecurityUtil.getUsername();
	}

	private String toString(String projectKey, String taskKey) {
		return projectKey + "/" + taskKey;
	}

	public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest) throws BusinessServiceException {
		try {
			Issue issue = getIssue(taskKey);
			final List<AuthoringTask> authoringTasks = buildAuthoringTasks(Collections.singletonList(issue), true);
			if (authoringTasks.isEmpty()) {
			    throw new ResourceNotFoundException("Task", taskKey);
            }
			AuthoringTask authoringTask = authoringTasks.get(0);
			// Act on each field received
			final TaskStatus status = taskUpdateRequest.getStatus();

			if (status != null) {
				Status currentStatus = issue.getStatus();
				// Don't attempt to transition to the same status
				if (!status.getLabel().equalsIgnoreCase(currentStatus.getName())) {
					if (status == TaskStatus.UNKNOWN) {
						throw new BadRequestException("Requested status is unknown.");
					}
					stateTransition(issue, status, projectKey);

					// Send email to Author once the task has been reviewed completely
					if (TaskStatus.REVIEW_COMPLETED.equals(status)) {
						emailService.sendTaskReviewCompletedNotification(projectKey, taskKey, authoringTask.getSummary(), Collections.singleton(authoringTask.getAssignee()));
					}
				}
			}

			final Issue.FluentUpdate updateRequest = issue.update();
			boolean fieldUpdates = false;

			final org.ihtsdo.authoringservices.domain.User assignee = taskUpdateRequest.getAssignee();
			TaskTransferRequest taskTransferRequest = null;
			if (assignee != null) {
				final String username = assignee.getUsername();
				if (username == null || username.isEmpty()) {
					updateRequest.field(Field.ASSIGNEE, null);
				} else {
					updateRequest.field(Field.ASSIGNEE, getUser(username));
					String currentUser = issue.getAssignee().getName();
					if (currentUser != null && !currentUser.isEmpty() && !currentUser.equalsIgnoreCase(username)) {
						taskTransferRequest = new TaskTransferRequest(currentUser, username);
					}
				}
				fieldUpdates = true;
			}

			final List<org.ihtsdo.authoringservices.domain.User> reviewers = taskUpdateRequest.getReviewers();
			if (reviewers != null) {
				if (reviewers.size() > 0) {
					List<User> users = new ArrayList<>();

                    for (org.ihtsdo.authoringservices.domain.User reviewer : reviewers) {
                        users.add(getUser(reviewer.getUsername()));
                    }
					// Send email to new reviewers
					emailService.sendTaskReviewAssignedNotification(projectKey, taskKey, authoringTask.getSummary(), getNewReviewers(authoringTask.getReviewers(), reviewers));

					updateRequest.field(AuthoringTask.jiraReviewerField, null);
					updateRequest.field(AuthoringTask.jiraReviewersField, users);
					fieldUpdates = true;
				} else {
					updateRequest.field(AuthoringTask.jiraReviewerField, null);
					updateRequest.field(AuthoringTask.jiraReviewersField, null);
					fieldUpdates = true;
				}
			}

			final String summary = taskUpdateRequest.getSummary();
			if (summary != null) {
				updateRequest.field(Field.SUMMARY, summary);
				fieldUpdates = true;
			}

			final String description = taskUpdateRequest.getDescription();
			if (description != null) {
				updateRequest.field(Field.DESCRIPTION, description);
				fieldUpdates = true;
			}

			if (fieldUpdates) {
				updateRequest.execute();
				// If the JIRA update goes through, then we can move any
				// UI-State over if required
				if (taskTransferRequest != null) {
					try {
						uiService.transferTask(projectKey, taskKey, taskTransferRequest);
						String taskReassignMessage = "Your task %s has been assigned to %s";
						String taskTakenMessage = "The task %s has been assigned to you by %s";
						if (!SecurityUtil.getUsername().equalsIgnoreCase(taskTransferRequest.getCurrentUser()) &&
								!SecurityUtil.getUsername().equalsIgnoreCase(taskTransferRequest.getNewUser())) {
							User newUser = getUser(taskTransferRequest.getNewUser());
							User currentLoggedUser = getUser(SecurityUtil.getUsername());
							String message = String.format(taskReassignMessage, taskKey, newUser.getDisplayName());
							notificationService.queueNotification(taskTransferRequest.getCurrentUser(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
							message = String.format(taskTakenMessage, taskKey, currentLoggedUser.getDisplayName());
							notificationService.queueNotification(taskTransferRequest.getNewUser(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
						} else if (taskTransferRequest.getNewUser().equalsIgnoreCase(SecurityUtil.getUsername())) {
							User user = getUser(taskTransferRequest.getNewUser());
							String message = String.format(taskReassignMessage, taskKey, user.getDisplayName());
							notificationService.queueNotification(taskTransferRequest.getCurrentUser(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
						} else {
							User user = getUser(taskTransferRequest.getCurrentUser());
							String message = String.format(taskTakenMessage, taskKey, user.getDisplayName());
							notificationService.queueNotification(taskTransferRequest.getNewUser(), new Notification(projectKey, taskKey, EntityType.AuthorChange, message));
						}
					} catch (BusinessServiceException e) {
						logger.error("Unable to transfer UI State in " + taskKey + " from "
								+ taskTransferRequest.getCurrentUser() + " to " + taskTransferRequest.getNewUser(), e);
					}
				}
			}
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to update task.", e);
		}

		// Pick up those changes in a new Task object
		return retrieveTask(projectKey, taskKey, false);
	}

	public AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException {
		List<Issue> issues = Collections.singletonList(getProjectTicketOrThrow(projectKey));
		if (issues.size() == 0) {
			throw new BusinessServiceException ("Failed to recover project: " + projectKey);
		}
		Issue project = issues.get(0);


		// For some reason, it's not possible to update some custom fields in type of
		// - Radio button
		// - Checkbox
		// - Select List
		// by using Jira Client. So we have to use REST api to update those fields manually.

		boolean changed = false;
		JSONObject fieldmap = new JSONObject();
		final Boolean projectScheduledRebaseDisabled = updatedProject.isProjectScheduledRebaseDisabled();
		if (projectScheduledRebaseDisabled != null) {
			String projectScheduledRebaseFieldOldVal =  JiraHelper.toStringOrNull(project.getField(jiraProjectScheduledRebaseField));
			if ((projectScheduledRebaseFieldOldVal == null && projectScheduledRebaseDisabled != null)
				|| (projectScheduledRebaseFieldOldVal.equals(ENABLED_TEXT) && projectScheduledRebaseDisabled)
				|| (projectScheduledRebaseFieldOldVal.equals(DISABLED_TEXT) && !projectScheduledRebaseDisabled)) {
				JSONObject updateObj = new JSONObject();
				updateObj.put("value", Boolean.TRUE.equals(projectScheduledRebaseDisabled) ? DISABLED_TEXT : ENABLED_TEXT);
				fieldmap.put(jiraProjectScheduledRebaseField, updateObj);
				changed = true;
			}

		}

		final Boolean taskPromotionDisabled = updatedProject.isTaskPromotionDisabled();
		if (taskPromotionDisabled != null) {
			String taskPromotionFieldOldVal = JiraHelper.toStringOrNull(project.getField(jiraTaskPromotionField));
			if ((taskPromotionFieldOldVal == null && taskPromotionDisabled != null)
				|| (taskPromotionFieldOldVal.equals(ENABLED_TEXT) && taskPromotionDisabled)
				|| (taskPromotionFieldOldVal.equals(DISABLED_TEXT) && !taskPromotionDisabled)) {
				JSONObject updateObj = new JSONObject();
				updateObj.put("value", Boolean.TRUE.equals(taskPromotionDisabled) ? DISABLED_TEXT : ENABLED_TEXT);
				fieldmap.put(jiraTaskPromotionField, updateObj);
				changed = true;
			}
		}
		if (changed) {
			JSONObject req = new JSONObject();
			req.put("fields", fieldmap);
			try {
				RestClient restclient = getJiraClient().getRestClient();
				URI uri = new URI(project.getSelf());
				restclient.put(uri, req);
			} catch (RestException | IOException | URISyntaxException e) {
				throw new BusinessServiceException("Failed to update SCA Project Scheduled Rebase.", e);
			}
		}

		return retrieveProject(projectKey);
	}

	private Collection<org.ihtsdo.authoringservices.domain.User> getNewReviewers(List<org.ihtsdo.authoringservices.domain.User> currentReviewers,
                                                                                 List<org.ihtsdo.authoringservices.domain.User> reviewers) throws BusinessServiceException {
		reviewers.removeAll(currentReviewers);
		Collection<org.ihtsdo.authoringservices.domain.User> results =
                reviewers.stream().filter(r -> !SecurityUtil.getUsername().equals(r.getUsername())).collect(Collectors.toList());

		for (org.ihtsdo.authoringservices.domain.User user : results) {
			if (user.getEmail() == null || user.getDisplayName() == null) {
				User jiraUser = getUser(user.getUsername());
				user.setEmail(jiraUser.getEmail());
				user.setDisplayName(jiraUser.getDisplayName());
			}
		}
		return  results;
	}

	private org.ihtsdo.authoringservices.domain.User getPojoUserOrNull(User lead) {
		org.ihtsdo.authoringservices.domain.User leadUser = null;
		if (lead != null) {
			leadUser = new org.ihtsdo.authoringservices.domain.User(lead);
		}
		return leadUser;
	}

	private User getUser(String username) throws BusinessServiceException {
		try {
			return User.get(getJiraClient().getRestClient(), username);
		} catch (JiraException je) {
			throw new BusinessServiceException("Failed to recover user '" + username + "' from Jira instance.", je);
		}
	}

	/**
	 * Returns the most recent Date/time of when the specified field changed to
	 * the specified value
	 *
	 * @throws BusinessServiceException
	 */
	public Date getDateOfChange(String projectKey, String taskKey, String fieldName, String newValue)
			throws JiraException, BusinessServiceException {

		try {
			// Recover the change log for the issue and work through it to find
			// the change specified
			ChangeLog changeLog = getIssue(taskKey, true).getChangeLog();
			if (changeLog != null) {
				// Sort changeLog entries descending to get most recent change
				// first
				Collections.sort(changeLog.getEntries(), CHANGELOG_ID_COMPARATOR_DESC);
				for (ChangeLogEntry entry : changeLog.getEntries()) {
					Date thisChangeDate = entry.getCreated();
					for (ChangeLogItem changeItem : entry.getItems()) {
						if (changeItem.getField().equals(fieldName) && changeItem.getToString().equals(newValue)) {
							return thisChangeDate;
						}
					}
				}
			}
		} catch (JiraException je) {
			throw new BusinessServiceException(
					"Failed to recover change log from task " + toString(projectKey, taskKey), je);
		}
		return null;
	}

	public static Comparator<ChangeLogEntry> CHANGELOG_ID_COMPARATOR_DESC = (entry1, entry2) -> {
		Integer id1 = Integer.parseInt(entry1.getId());
		Integer id2 = Integer.parseInt(entry2.getId());
		return id2.compareTo(id1);
	};

	public boolean conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState,
			TaskStatus newState) throws JiraException, BusinessServiceException {
		final Issue issue = getIssue(taskKey);
		if (TaskStatus.fromLabel(issue.getStatus().getName()) == requiredState) {
			stateTransition(issue, newState, projectKey);
			return true;
		}
		return false;
	}

	public void stateTransition(String projectKey, String taskKey, TaskStatus newState)
			throws BusinessServiceException {
		try {
			stateTransition(getIssue(taskKey), newState, projectKey);
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to transition state of task " + taskKey, e);
		}
	}

	public void stateTransition(List<Issue> issues, TaskStatus newState, String projectKey) {
		for (Issue issue : issues) {
			try {
				stateTransition(issue, newState, projectKey);
			} catch (JiraException | BusinessServiceException e) {
				logger.error("Failed to transition issue {} to {}", issue.getKey(), newState.getLabel());
			}
		}
	}

	private void stateTransition(Issue issue, TaskStatus newState, String projectKey) throws JiraException, BusinessServiceException {
		final Transition transition = getTransitionToOrThrow(issue, newState);
		final String key = issue.getKey();
		final String newStateLabel = newState.getLabel();
		logger.info("Transition issue {} to {}", key, newStateLabel);
		issue.transition().execute(transition);
		issue.refresh();

		if (!taskStateChangeNotificationQueues.isEmpty()) {
			// Send JMS Task State Notification
			try {
				Map<String, String> properties = new HashMap<>();
				properties.put("key", key);
				properties.put("status", newStateLabel);

				if (TaskStatus.COMPLETED.equals(newState)) {
					try {
						String conceptsStr = uiService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(projectKey, issue.getKey(), issue.getAssignee().getName(), "crs-concepts");
						if (StringUtils.isNotEmpty(conceptsStr)) {
							properties.put("concepts", conceptsStr);
						}
					} catch (IOException e) {
						logger.error("Error while reading crs-concepts.json for task {}. Message: {}", issue.getKey(), e.getMessage());
					}
				}

				// To comma separated list
				final String labelsString = issue.getLabels().toString();
				properties.put("labels", labelsString.substring(1, labelsString.length() - 1).replace(", ", ","));

				for (String queue : taskStateChangeNotificationQueues) {
					messagingHelper.send(new ActiveMQQueue(queue), properties);
				}
			} catch (JsonProcessingException | JMSException e) {
				logger.error("Failed to send task state change notification for {} {}.", key, newStateLabel, e);
			}
		}
	}

	private Transition getTransitionToOrThrow(Issue issue, TaskStatus newState)
			throws JiraException, BusinessServiceException {
		for (Transition transition : issue.getTransitions()) {
			if (transition.getToStatus().getName().equals(newState.getLabel())) {
				return transition;
			}
		}
		throw new BadRequestException("Could not transition task " + issue.getKey() + " from status '"
				+ issue.getStatus().getName() + "' to '" + newState.name() + "', no such transition is available.");
	}

	public List<TaskAttachment> getTaskAttachments(String projectKey, String taskKey) throws BusinessServiceException {

		List<TaskAttachment> attachments = new ArrayList<>();
		final RestClient restClient = getJiraClient().getRestClient();

		try {
			Issue issue = getIssue(taskKey);
		
			for (IssueLink issueLink : issue.getIssueLinks()) {

				Issue linkedIssue = issueLink.getOutwardIssue();

				// need to forcibly retrieve the issue in order to get
				// attachments
				Issue issue1 = this.getIssue(linkedIssue.getKey(), true);
				Object organization = issue1.getField(jiraOrganizationField);
				String crsId = issue1.getField(jiraCrsIdField).toString();
				if (crsId == null) {
					crsId = "Unknown";
				}

				boolean attachmentFound = false;

				for (Attachment attachment : issue1.getAttachments()) {

					if (attachment.getFileName().equals("request.json")) {

						// attachments must be retrieved by relative path --
						// absolute path will redirect to login
						try {
							final String contentUrl = attachment.getContentUrl();
							final JSON attachmentJson = restClient
									.get(contentUrl.substring(contentUrl.indexOf("secure")));

							TaskAttachment taskAttachment = new TaskAttachment(issue1.getKey(), crsId, attachmentJson.toString(), organization != null ? organization.toString() : null);

							attachments.add(taskAttachment);
							
							attachmentFound = true;

						} catch (Exception e) {
							throw new BusinessServiceException(
									"Failed to retrieve attachment " + attachment.getContentUrl() + ": " + e.getMessage(), e);
						}
					}
				}
				
				// if no attachments, create a blank one to link CRS ticket id
 				if (!attachmentFound) {
 					TaskAttachment taskAttachment = new TaskAttachment(issue.getKey(), crsId, null, organization != null ? organization.toString() : null);
 					attachments.add(taskAttachment);
 				}	
				
			}
		} catch (JiraException e) {
			if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 404) {
				throw new ResourceNotFoundException("Task not found " + toString(projectKey, taskKey), e);
			}
			throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
		}

		return attachments;
	}

	public void leaveCommentForTask(String projectKey, String taskKey, String comment) throws BusinessServiceException {
		try {
			addComment(taskKey, comment);
		} catch (JiraException e) {
			if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 404) {
				throw new ResourceNotFoundException("Task not found " + toString(projectKey, taskKey), e);
			}
			throw new BusinessServiceException("Failed to leave comment for task " + toString(projectKey, taskKey), e);
		
		}
	}

	private List<JiraProject> getProjects(RestClient restClient) throws JiraException {
		try {
			URI ex = restClient.buildURI(Resource.getBaseUri() + "project", Collections.singletonMap("expand", "lead"));
			JSON response = restClient.get(ex);
			JSONArray projectsArray = JSONArray.fromObject(response);
			ArrayList<JiraProject> projects = new ArrayList<>();

			for(int i = 0; i < projectsArray.size(); ++i) {
				JSONObject p = projectsArray.getJSONObject(i);
				JSONObject leadObject = p.getJSONObject("lead");
				org.ihtsdo.authoringservices.domain.User lead = null;
				if (leadObject != null) {
					lead = new org.ihtsdo.authoringservices.domain.User(leadObject);
				}
				projects.add(new JiraProject(p.getString("key"), p.getString("name"), lead));
			}

			return projects;
		} catch (Exception var7) {
			throw new JiraException(var7.getMessage(), var7);
		}
	}

	@PreDestroy
	public void shutdown() {
		executorService.shutdown();
	}

}
