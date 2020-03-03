package org.ihtsdo.snowowl.authoring.single.api.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jms.JMSException;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Level;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.exception.BadRequestException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.snowowl.authoring.single.api.configuration.ConfigUtils;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringMain;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskUpdateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.JiraProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.TaskTransferRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.ihtsdo.snowowl.authoring.single.api.review.service.TaskMessagesDetail;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.JiraHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.TimerUtil;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import net.rcarz.jiraclient.Attachment;
import net.rcarz.jiraclient.ChangeLog;
import net.rcarz.jiraclient.ChangeLogEntry;
import net.rcarz.jiraclient.ChangeLogItem;
import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.IssueLink;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.Resource;
import net.rcarz.jiraclient.RestClient;
import net.rcarz.jiraclient.RestException;
import net.rcarz.jiraclient.Status;
import net.rcarz.jiraclient.Transition;
import net.rcarz.jiraclient.User;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class TaskService {

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
	private ClassificationService classificationService;

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

	@Value("${task-state-change.notification-queues}")
	private String taskStateChangeNotificationQueuesString;

	private Set<String> taskStateChangeNotificationQueues;

	private final ImpersonatingJiraClientFactory jiraClientFactory;
	private final String jiraExtensionBaseField;
	private final String jiraProductCodeField;
	private final String jiraProjectPromotionField;
	private final String jiraTaskPromotionField;
	private final String jiraProjectRebaseField;
	private final String jiraProjectScheduledRebaseField;
	private final String jiraProjectMrcmField;
	private final String jiraCrsIdField;
	private final String jiraProjectTemplatesField;
	private final String jiraProjectSpellCheckField;
	private final Set<String> projectJiraFetchFields;
	private final Set<String> myTasksRequiredFields;

	private LoadingCache<String, ProjectDetails> projectDetailsCache;
	private final ExecutorService executorService;
	private static final String UNIT_TEST = "UNIT_TEST";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TaskService(ImpersonatingJiraClientFactory jiraClientFactory, String jiraUsername) throws JiraException {
		this.jiraClientFactory = jiraClientFactory;
		executorService = Executors.newCachedThreadPool();

		if (!jiraUsername.equals(UNIT_TEST)){
			logger.info("Fetching Jira custom field names.");
			final JiraClient jiraClientForFieldLookup = jiraClientFactory.getAdminInstance();
			AuthoringTask.setJiraReviewerField(JiraHelper.fieldIdLookup("Reviewer", jiraClientForFieldLookup, null));
			AuthoringTask.setJiraReviewersField(JiraHelper.fieldIdLookup("Reviewers", jiraClientForFieldLookup, null));
			projectJiraFetchFields = new HashSet<>();
			projectJiraFetchFields.add("project");
			jiraExtensionBaseField = JiraHelper.fieldIdLookup("Extension Base", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProductCodeField = JiraHelper.fieldIdLookup("Product Code", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectPromotionField = JiraHelper.fieldIdLookup("SCA Project Promotion", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraTaskPromotionField = JiraHelper.fieldIdLookup("SCA Task Promotion", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectRebaseField = JiraHelper.fieldIdLookup("SCA Project Rebase", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectScheduledRebaseField = JiraHelper.fieldIdLookup("SCA Project Scheduled Rebase", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectMrcmField = JiraHelper.fieldIdLookup("SCA Project MRCM", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraCrsIdField = JiraHelper.fieldIdLookup("CRS-ID", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectTemplatesField = JiraHelper.fieldIdLookup("SCA Project Templates", jiraClientForFieldLookup, projectJiraFetchFields);
			jiraProjectSpellCheckField = JiraHelper.fieldIdLookup("SCA Project Spell Check", jiraClientForFieldLookup, projectJiraFetchFields);
			logger.info("Jira custom field names fetched. (e.g. {}).", jiraExtensionBaseField);
		
			init();
		} else {
			projectJiraFetchFields = null;
			jiraExtensionBaseField = null;
			jiraProductCodeField = null;
			jiraProjectPromotionField = null;
			jiraTaskPromotionField = null;
			jiraProjectRebaseField = null;
			jiraProjectScheduledRebaseField = null;
			jiraProjectMrcmField = null;
			jiraCrsIdField = null;
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
				.build(new CacheLoader<String, ProjectDetails>() {
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
		taskStateChangeNotificationQueues = ConfigUtils.getStringSet(taskStateChangeNotificationQueuesString);
		logger.info("{} task state notification queues configured {}", taskStateChangeNotificationQueues.size(), taskStateChangeNotificationQueues);
	}

	public List<AuthoringProject> listProjects(Boolean lightweight) throws JiraException, BusinessServiceException {
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
		final List<AuthoringProject> authoringProjects = buildAuthoringProjects(projectTickets, lightweight == null ? false : lightweight);
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

	private List<AuthoringProject> buildAuthoringProjects(List<Issue> projectTickets, boolean lightweight) throws BusinessServiceException {
		if (projectTickets.isEmpty()) {
			return new ArrayList<>();
		}
		final List<AuthoringProject> authoringProjects = new ArrayList<>();
		final Set<String> branchPaths = new HashSet<>();
		final Map<String, Branch> branchMap = new ConcurrentHashMap<>();
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
				final boolean taskPromotionDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraTaskPromotionField)));
				final boolean rebaseDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectRebaseField)));
				final boolean scheduledRebaseDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectScheduledRebaseField)));
				final boolean mrcmDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectMrcmField)));
				final boolean templatesDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectTemplatesField)));
				final boolean spellCheckDisabled = DISABLED_TEXT.equals(JiraHelper.toStringOrNull(projectTicket.getField(jiraProjectSpellCheckField)));

				final Branch branchOrNull = branchService.getBranchOrNull(branchPath);
				String parentPath = PathHelper.getParentPath(branchPath);
				Branch parentBranchOrNull = branchMap.get(parentPath);
				if (parentBranchOrNull == null) {
					parentBranchOrNull = branchService.getBranchOrNull(parentPath);
					if (parentBranchOrNull == null) {
						logger.error("Project {} expected parent branch does not exist: {}", projectKey, parentPath);
						return;
					}
					branchMap.put(parentPath, parentBranchOrNull);
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
						project.getLead(), branchPath, branchState, baseTimeStamp, headTimeStamp, latestClassificationJson, promotionDisabled, mrcmDisabled, templatesDisabled, spellCheckDisabled, rebaseDisabled, scheduledRebaseDisabled, taskPromotionDisabled);
				authoringProject.setMetadata(metadata);
				synchronized (authoringProjects) {
					authoringProjects.add(authoringProject);
				}
			} catch (RestClientException | ServiceException | InterruptedException | ExecutionException e) {
				logger.error("Failed to fetch details of project {}", projectTicket.getProject().getName(), e);
			}
		});

		if (!lightweight) {
			final ImmutableMap<String, String> validationStatuses;
			try {
				validationStatuses = validationService.getValidationStatuses(branchPaths);
				for (AuthoringProject authoringProject : authoringProjects) {
					String branchPath = authoringProject.getBranchPath();
					String validationStatus = validationStatuses.get(branchPath);
					authoringProject.setValidationStatus(validationStatus);
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
			final ImmutableMap<String, String> statuses = validationService.getValidationStatuses(paths);
			final String branchState = branchService.getBranchStateOrNull(PathHelper.getMainPath());
			final String latestClassificationJson = classificationService
					.getLatestClassification(PathHelper.getMainPath());
			return new AuthoringMain(path, branchState, statuses.get(path), latestClassificationJson);
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

	public AuthoringTask retrieveTask(String projectKey, String taskKey) throws BusinessServiceException {
		try {
			Issue issue = getIssue(projectKey, taskKey);
			final List<AuthoringTask> authoringTasks = buildAuthoringTasks(Collections.singletonList(issue), false);
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

	private Issue getIssue(String projectKey, String taskKey) throws JiraException {
		return getIssue(projectKey, taskKey, false);
	}

	private Issue getIssue(String projectKey, String taskKey, boolean includeAll) throws JiraException {
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
	 *
	 * @param criteria
	 * @param lightweight
	 * @return
	 * @throws JiraException
	 * @throws BusinessServiceException
	 */
	public List<AuthoringTask> searchTasks(String criteria, Boolean lightweight) throws JiraException, BusinessServiceException {
		if (StringUtils.isEmpty(criteria)) {
			return Collections.EMPTY_LIST;
		}
		String[] arrayStr = criteria.split("-");
		boolean isJiraId = arrayStr.length == 2 && NumberUtils.isNumber(arrayStr[1]);
		String jql = "type = \"" + AUTHORING_TASK_TYPE + "\"" + " AND status != \"" + TaskStatus.DELETED.getLabel() + "\"" + (isJiraId ? " AND id = \"" + criteria + "\"" : " AND summary ~ \"" + criteria + "\"");
		List<Issue> issues;
		try {
			issues = searchIssues(jql, LIMIT_UNLIMITED, myTasksRequiredFields);
		} catch (JiraException exception) {
			if (isJiraId) {
				return Collections.EMPTY_LIST;
			} else {
				throw exception;
			}
		}
		return buildAuthoringTasks(issues, lightweight == null ? false : lightweight);
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
		String jql = "type = \"" + AUTHORING_TASK_TYPE + "\" " + "AND assignee != currentUser() "
				+ "AND (Reviewer = currentUser() OR Reviewers = currentUser() OR (Reviewer = null AND Reviewers = null AND status = \"" + TaskStatus.IN_REVIEW.getLabel()
				+ "\")) " + EXCLUDE_STATUSES;
		if (null != excludePromoted && excludePromoted.equalsIgnoreCase("TRUE")) {
			jql += " AND status != \"Promoted\"";
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
					final ImmutableMap<String, String> validationStatuses = validationService.getValidationStatuses(paths);
					timer.checkpoint("Recovered " + (validationStatuses == null ? "null" : validationStatuses.size()) + " ValidationStatuses");

					if (validationStatuses == null || validationStatuses.isEmpty()) {
						logger.warn("Failed to recover validation statuses for {} branches including '{}'.", paths.size(), paths.iterator().next());
					} else {
						for (final String path : paths) {
							startedTasks.get(path).setLatestValidationStatus(validationStatuses.get(path));
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

	public boolean taskIsState(String projectKey, String taskKey, TaskStatus state) throws JiraException {
		Issue issue = getIssue(projectKey, taskKey);
		String currentState = issue.getStatus().getName();
		return currentState.equals(state.getLabel());
	}

	public void addCommentLogErrors(String projectKey, String commentString) {
		final Issue projectTicket = getProjectTicketOrThrow(projectKey);
		addCommentLogErrors(projectKey, projectTicket.getKey(), commentString);
	}

	public void addCommentLogErrors(String projectKey, String taskKey, String commentString) {
		try {
			addComment(projectKey, taskKey, commentString);
		} catch (JiraException e) {
			logger.error("Failed to set message on jira ticket {}/{}: {}", projectKey, taskKey, commentString, e);
		}
	}

	public void addComment(String projectKey, String taskKey, String commentString) throws JiraException {
		Issue issue = getIssue(projectKey, taskKey);
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

	public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest)
			throws BusinessServiceException {
		try {
			Issue issue = getIssue(projectKey, taskKey);
			// Act on each field received
			final TaskStatus status = taskUpdateRequest.getStatus();

			if (status != null) {
				Status currentStatus = issue.getStatus();
				// Don't attempt to transition to the same status
				if (!status.getLabel().equalsIgnoreCase(currentStatus.getName())) {
					if (status == TaskStatus.UNKNOWN) {
						throw new BadRequestException("Requested status is unknown.");
					}
					stateTransition(issue, status);
				}
			}

			final Issue.FluentUpdate updateRequest = issue.update();
			boolean fieldUpdates = false;

			final org.ihtsdo.snowowl.authoring.single.api.pojo.User assignee = taskUpdateRequest.getAssignee();
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

			final List<org.ihtsdo.snowowl.authoring.single.api.pojo.User> reviewers = taskUpdateRequest.getReviewers();
			if (reviewers != null) {
				if (reviewers.size() > 0) {
					List<User> users = new ArrayList<User>();
					
					for (int i = 0 ; i < reviewers.size(); i++) {
						org.ihtsdo.snowowl.authoring.single.api.pojo.User reviewer = reviewers.get(i);
						users.add(getUser(reviewer.getUsername()));
					}
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
		return retrieveTask(projectKey, taskKey);
	}

	public AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException {
		try {
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
			final Boolean projectScheduledRebaseDisabled = updatedProject.isProjectScheduledRebaseDisabled();
			if (projectScheduledRebaseDisabled != null) {
				JSONObject obj = (JSONObject) project.getField(jiraProjectScheduledRebaseField);
				String oldVal = obj.getString("value");
				boolean changed = (oldVal.equals(ENABLED_TEXT) && projectScheduledRebaseDisabled) || (oldVal.equals(DISABLED_TEXT) && !projectScheduledRebaseDisabled);
				if (changed) {
					/*if(!project.getReporter().getName().equals(getUsername())) {
						throw new BusinessServiceException ("No permisson to turn on/off automatic rebase.");
					}*/
					
					JSONObject updateObj = new JSONObject();
					updateObj.put("value", projectScheduledRebaseDisabled ? DISABLED_TEXT : ENABLED_TEXT);
					
					JSONObject fieldmap = new JSONObject();
					fieldmap.put(jiraProjectScheduledRebaseField, updateObj);
					
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
			}
			
			// TO DO
			// Remove the follow-ng block of code if there is further implementation
			final Issue.FluentUpdate updateRequest = project.update();
			boolean fieldUpdates = false;
			if (fieldUpdates) {
				updateRequest.execute();
			}
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to update project.", e);
		}
		
		return retrieveProject(projectKey);
	}
	
	private org.ihtsdo.snowowl.authoring.single.api.pojo.User getPojoUserOrNull(User lead) {
		org.ihtsdo.snowowl.authoring.single.api.pojo.User leadUser = null;
		if (lead != null) {
			leadUser = new org.ihtsdo.snowowl.authoring.single.api.pojo.User(lead);
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
			ChangeLog changeLog = getIssue(projectKey, taskKey, true).getChangeLog();
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

	public static Comparator<ChangeLogEntry> CHANGELOG_ID_COMPARATOR_DESC = new Comparator<ChangeLogEntry>() {
		public int compare(ChangeLogEntry entry1, ChangeLogEntry entry2) {
			Integer id1 = new Integer(entry1.getId());
			Integer id2 = new Integer(entry2.getId());
			return id2.compareTo(id1);
		}
	};

	private org.ihtsdo.snowowl.authoring.single.api.pojo.Status status;

	public boolean conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState,
			TaskStatus newState) throws JiraException, BusinessServiceException {
		final Issue issue = getIssue(projectKey, taskKey);
		if (TaskStatus.fromLabel(issue.getStatus().getName()) == requiredState) {
			stateTransition(issue, newState);
			return true;
		}
		return false;
	}

	public void stateTransition(String projectKey, String taskKey, TaskStatus newState)
			throws BusinessServiceException {
		try {
			stateTransition(getIssue(projectKey, taskKey), newState);
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to transition state of task " + taskKey, e);
		}
	}

	public void stateTransition(List<Issue> issues, TaskStatus newState) {
		for (Issue issue : issues) {
			try {
				stateTransition(issue, newState);
			} catch (JiraException | BusinessServiceException e) {
				logger.error("Failed to transition issue {} to {}", issue.getKey(), newState.getLabel());
			}
		}
	}

	private void stateTransition(Issue issue, TaskStatus newState) throws JiraException, BusinessServiceException {
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
			Issue issue = getIssue(projectKey, taskKey);
		
			for (IssueLink issueLink : issue.getIssueLinks()) {

				Issue linkedIssue = issueLink.getOutwardIssue();

				// need to forcibly retrieve the issue in order to get
				// attachments
				Issue issue1 = this.getIssue(null, linkedIssue.getKey(), true);

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

							TaskAttachment taskAttachment = new TaskAttachment(issue1.getKey(), crsId, attachmentJson.toString());

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
 					TaskAttachment taskAttachment = new TaskAttachment(issue.getKey(), crsId, null);
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
			addComment(projectKey, taskKey, comment);
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
				org.ihtsdo.snowowl.authoring.single.api.pojo.User lead = null;
				if (leadObject != null) {
					lead = new org.ihtsdo.snowowl.authoring.single.api.pojo.User(leadObject);
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
