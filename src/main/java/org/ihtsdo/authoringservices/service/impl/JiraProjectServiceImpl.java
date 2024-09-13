package org.ihtsdo.authoringservices.service.impl;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import jakarta.annotation.PreDestroy;
import net.rcarz.jiraclient.*;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.service.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
import org.ihtsdo.authoringservices.service.util.TimerUtil;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JiraProjectServiceImpl implements ProjectService {
    private final Logger logger = LoggerFactory.getLogger(JiraProjectServiceImpl.class);

    public static final String UNIT_TEST = "UNIT_TEST";
    public static final int LIMIT_UNLIMITED = -1;

    private static final String AUTHORING_PROJECT_TYPE = "SCA Authoring Project";
    private static final String ENABLED_TEXT = "Enabled";
    private static final String DISABLED_TEXT = "Disabled";
    private static final String VALUE = "value";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_LEAD = "lead";
    private static final String STRING_TYPE = "string";
    private static final String OPTION_TYPE = "option";
    private static final String NULL_STRING = "null";

    private static final String FAILED_TO_RECOVER_PROJECT_MSG = "Failed to recover project: ";


    @Value("${jira.issue.custom.fields}")
    private List<String> requiredCustomFields;

    @Value("${jira.project.creation.defaultProjectTemplateKey}")
    private String defaultProjectTemplateKey;

    private LoadingCache<String, ProjectDetails> projectDetailsCache;

    private final ImpersonatingJiraClientFactory jiraClientFactory;

    private final String jiraExtensionBaseField;
    private final String jiraProductCodeField;
    private final String jiraProjectPromotionField;
    private final String jiraProjectLockedField;
    private final String jiraTaskPromotionField;
    private final String jiraProjectRebaseField;
    private final String jiraProjectScheduledRebaseField;
    private final String jiraProjectMrcmField;
    private final String jiraProjectTemplatesField;
    private final String jiraProjectSpellCheckField;
    private final Set<String> projectJiraFetchFields;

    private final ExecutorService executorService;

    @Autowired
    private InstanceConfiguration instanceConfiguration;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Autowired
    private SnowstormClassificationClient classificationService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private TaskService taskService;

    public JiraProjectServiceImpl(ImpersonatingJiraClientFactory jiraClientFactory, String jiraUsername) throws JiraException {
        this.jiraClientFactory = jiraClientFactory;
        executorService = Executors.newCachedThreadPool();
        if (!jiraUsername.equals(UNIT_TEST)) {
            logger.info("Fetching Jira custom field names.");
            final JiraClient jiraClientForFieldLookup = jiraClientFactory.getAdminInstance();

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
            jiraProjectTemplatesField = null;
            jiraProjectSpellCheckField = null;
        }
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
                        Set<String> allKeys = new HashSet<>();
                        keys.forEach(item -> allKeys.add(String.valueOf(item)));
                        final Map<String, Issue> projectTickets = getProjectTickets(allKeys);
                        Map<String, ProjectDetails> keyToBaseMap = new HashMap<>();
                        for (Map.Entry<String, Issue> entry : projectTickets.entrySet()) {
                            keyToBaseMap.put(entry.getKey(),
                                    getProjectDetailsPopulatingCache(projectTickets.get(entry.getKey())));
                        }
                        return keyToBaseMap;
                    }
                });
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

    @Override
    public LoadingCache<String, ProjectDetails> getProjectDetailsCache() {
        return projectDetailsCache;
    }

    @Override
    public void deleteProject(String projectKey) throws BusinessServiceException {
        logger.info("Deleting JIRA project {}", projectKey);
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        try {
            JiraHelper.deleteProject(adminJiraClient, projectKey);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to delete JIRA project " + projectKey, e);
        }
    }

    @Override
    public List<AuthoringProjectField> retrieveProjectCustomFields(String projectKey) throws BusinessServiceException {
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        Issue issue = getProjectTicket(projectKey);
        if (issue == null) throw new ResourceNotFoundException("No magic ticket found for project " + projectKey);

        List<AuthoringProjectField> allCustomFields = new ArrayList<>();
        JSONArray fields = getAllFieldsFromJira(adminJiraClient);
        for (String requiredCustomField : requiredCustomFields) {
            for (int i = 0; i < fields.size(); i++) {
                final JSONObject jsonObject = fields.getJSONObject(i);
                String fieldName = jsonObject.getString("name");
                if (requiredCustomField.equals(fieldName)) {
                    final JSONObject typeObject = jsonObject.getJSONObject("schema");

                    AuthoringProjectField authoringProjectField = new AuthoringProjectField();
                    String fieldId = jsonObject.getString("id");
                    authoringProjectField.setId(fieldId);
                    authoringProjectField.setName(jsonObject.getString("name"));

                    String type = typeObject.getString("type");
                    authoringProjectField.setType(type);
                    setFieldValue(authoringProjectField, type, issue, fieldId);

                    allCustomFields.add(authoringProjectField);
                    break;
                }
            }
        }

        return allCustomFields;
    }

    private void setFieldValue(AuthoringProjectField authoringProjectField, String type, Issue issue, String fieldId) {
        if (null != issue.getField(fieldId)) {
            Object value = issue.getField(fieldId);
            switch (type) {
                case STRING_TYPE -> authoringProjectField.setValue(NULL_STRING.equals(value.toString()) ? null : value.toString());
                case OPTION_TYPE -> authoringProjectField.setValue(((JSONObject) value).getString(VALUE));
                default -> logger.warn("Custom field type {} has not been supported", type);
            }
        }
    }

    private static JSONArray getAllFieldsFromJira(JiraClient adminJiraClient) throws BusinessServiceException {
        JSONArray fields;
        try {
            fields = JiraHelper.getFields(adminJiraClient);
        } catch (URISyntaxException | RestException | IOException e) {
            throw new BusinessServiceException("Failed to get JIRA field", e);
        }
        return fields;
    }

    @Override
    public List<AuthoringProject> listProjects(Boolean lightweight, Boolean ignoreProductCodeFilter) throws BusinessServiceException {
        final TimerUtil timer = new TimerUtil("ProjectsList");
        List<Issue> projectTickets = new ArrayList<>();
        // Search for authoring project tickets this user has visibility of
        List<Issue> issues;
        try {
            issues = searchIssues("type = \"SCA Authoring Project\"", LIMIT_UNLIMITED, projectJiraFetchFields);
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to list projects", e);
        }

        timer.checkpoint("First jira search");
        for (Issue projectMagicTicket : issues) {
            final String productCode = getProjectDetailsPopulatingCache(projectMagicTicket).productCode();
            if (instanceConfiguration.isJiraProjectVisible(productCode) || Boolean.TRUE.equals(ignoreProductCodeFilter)) {
                projectTickets.add(projectMagicTicket);
            }
        }
        timer.checkpoint("Jira searches");
        final List<AuthoringProject> authoringProjects = buildAuthoringProjects(projectTickets, Boolean.TRUE.equals(lightweight));
        if (Boolean.TRUE.equals(lightweight)) {
            timer.checkpoint("project path search only without validation and classification");
        } else {
            timer.checkpoint("validation and classification");
        }
        timer.finish();
        return authoringProjects;
    }

    @Override
    public AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException {
        List<Issue> issues = Collections.singletonList(getProjectTicket(projectKey));
        List<AuthoringProject> projects = buildAuthoringProjects(issues, false);
        if (projects.isEmpty()) {
            throw new BusinessServiceException(FAILED_TO_RECOVER_PROJECT_MSG + projectKey + ". See earlier logs for reason");
        }
        return projects.get(0);
    }

    @Override
    public AuthoringProject retrieveProject(String projectKey, boolean lightweight) throws BusinessServiceException {
        List<Issue> issues = Collections.singletonList(getProjectTicket(projectKey));
        List<AuthoringProject> projects = buildAuthoringProjects(issues, lightweight);
        if (projects.isEmpty()) {
            throw new BusinessServiceException(FAILED_TO_RECOVER_PROJECT_MSG + projectKey + ". See earlier logs for reason");
        }
        return projects.get(0);
    }

    @Override
    public void lockProject(String projectKey) throws BusinessServiceException {
        Issue issue = getProjectTicket(projectKey);
        if (issue != null) {
            try {
                JSONObject updateObj = new JSONObject();
                updateObj.put(VALUE, ENABLED_TEXT);

                final Issue.FluentUpdate updateRequest = issue.update();
                updateRequest.field(jiraProjectLockedField, updateObj);

                updateRequest.execute();
            } catch (JiraException e) {
                logger.error("Failed to update issue with key {}", issue.getKey(), e);
                throw new BusinessServiceException("Failed to update issue with key " + issue.getKey());
            }
        }
    }

    @Override
    public void unlockProject(String projectKey) throws BusinessServiceException {
        Issue issue = getProjectTicket(projectKey);
        if (issue != null) {
            try {
                JSONObject updateObj = new JSONObject();
                updateObj.put(VALUE, DISABLED_TEXT);

                final Issue.FluentUpdate updateRequest = issue.update();
                updateRequest.field(jiraProjectLockedField, updateObj);

                updateRequest.execute();
            } catch (JiraException e) {
                throw new BusinessServiceException("Failed to update issue with key " + issue.getKey(), e);
            }
        }
    }

    @Override
    public String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException {
        try {
            return projectDetailsCache.get(projectKey).baseBranchPath();
        } catch (ExecutionException e) {
            throw new BusinessServiceException("Failed to retrieve project path.", e);
        }
    }

    public Issue getProjectTicket(String projectKey) throws BusinessServiceException {
        return getProjectTickets(Collections.singleton(projectKey)).get(projectKey);
    }

    private Map<String, Issue> getProjectTickets(Set<String> projectKeys)
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
        } catch (Exception e) {
            if (Lists.newArrayList(projectKeys).size() == 1
                    && e.getCause() != null
                    && e.getCause().getCause() != null
                    && e.getCause().getCause() instanceof RestException restException) {
                String projectKey = projectKeys.iterator().next();
                String errorMessage = "The value '" + projectKey + "' does not exist for the field 'project'.";
                if (restException.getHttpStatusCode() == HttpStatus.BAD_REQUEST.value() && restException.getMessage().contains(errorMessage)) {
                    throw new ResourceNotFoundException(String.format("Project %s not found.", projectKey));
                }
            }
            throw new BusinessServiceException("Failed to load Projects from Jira.", e);
        }
    }

    @Override
    public AuthoringProject createProject(CreateProjectRequest request, String codeSystemBranchPath) throws BusinessServiceException {
        logger.info("Creating new project {}", request.key());
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        Project newProject;
        Project projectTemplate;
        String projectTemplateKey = request.projectTemplateKey() != null ? request.projectTemplateKey() : defaultProjectTemplateKey;
        try {
            projectTemplate = Project.get(adminJiraClient.getRestClient(), projectTemplateKey);
            newProject = JiraHelper.createProject(adminJiraClient, request, projectTemplate.getId());
        } catch (JiraException e) {
            throw new BusinessServiceException(String.format("Failed to create JIRA project %s. Error: %s", request.key(), e.getMessage()), e);
        }

        addRolesToProject(projectTemplate, adminJiraClient, projectTemplateKey, newProject);

        createMagicTicketForProject(request, codeSystemBranchPath.startsWith("MAIN/SNOMEDCT-") ? codeSystemBranchPath : null);

        return new AuthoringProject(newProject.getKey(), newProject.getName(),
                new org.ihtsdo.authoringservices.domain.User(newProject.getLead()), null, null, null, null, null, false, false, false, false, false, false, false, false);
    }

    @Override
    public AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException {
        Issue project = getProjectTicket(projectKey);

        if (project == null) {
            throw new BusinessServiceException(FAILED_TO_RECOVER_PROJECT_MSG + projectKey);
        }

        // For some reason, it's not possible to update some custom fields in type of
        // - Radio button
        // - Checkbox
        // - Select List
        // by using Jira Client. So we have to use REST api to update those fields manually.

        JSONObject fieldmap = new JSONObject();
        final Boolean projectScheduledRebaseDisabled = updatedProject.isProjectScheduledRebaseDisabled();
        final Boolean taskPromotionDisabled = updatedProject.isTaskPromotionDisabled();
        boolean shouldUpdateScheduleRebaseField = updateProjectScheduleRebaseField(project, projectScheduledRebaseDisabled, fieldmap);
        boolean shouldUpdateTaskPromotionField = updateProjectTaskPromotionField(project, taskPromotionDisabled, fieldmap);
        if (shouldUpdateScheduleRebaseField || shouldUpdateTaskPromotionField) {
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

    private boolean updateProjectTaskPromotionField(Issue project, Boolean taskPromotionDisabled, JSONObject fieldmap) {
        if (taskPromotionDisabled != null) {
            String taskPromotionFieldOldVal = JiraHelper.toStringOrNull(project.getField(jiraTaskPromotionField));
            if (taskPromotionFieldOldVal == null || (taskPromotionFieldOldVal.equals(ENABLED_TEXT) && taskPromotionDisabled) || (DISABLED_TEXT.equals(taskPromotionFieldOldVal) && !taskPromotionDisabled)) {
                JSONObject updateObj = new JSONObject();
                updateObj.put(VALUE, Boolean.TRUE.equals(taskPromotionDisabled) ? DISABLED_TEXT : ENABLED_TEXT);
                fieldmap.put(jiraTaskPromotionField, updateObj);
                return true;
            }
        }
        return false;
    }

    private boolean updateProjectScheduleRebaseField(Issue project, Boolean projectScheduledRebaseDisabled, JSONObject fieldmap) {
        if (projectScheduledRebaseDisabled != null) {
            String projectScheduledRebaseFieldOldVal = JiraHelper.toStringOrNull(project.getField(jiraProjectScheduledRebaseField));
            if (projectScheduledRebaseFieldOldVal == null || (projectScheduledRebaseFieldOldVal.equals(ENABLED_TEXT) && projectScheduledRebaseDisabled) || (projectScheduledRebaseFieldOldVal.equals(DISABLED_TEXT) && !projectScheduledRebaseDisabled)) {
                JSONObject updateObj = new JSONObject();
                updateObj.put(VALUE, Boolean.TRUE.equals(projectScheduledRebaseDisabled) ? DISABLED_TEXT : ENABLED_TEXT);
                fieldmap.put(jiraProjectScheduledRebaseField, updateObj);
                return true;
            }
        }
        return false;
    }

    @Override
    public void addCommentLogErrors(String projectKey, String commentString) throws BusinessServiceException {
        final Issue projectTicket = getProjectTicket(projectKey);
        taskService.addCommentLogErrors(projectKey, projectTicket.getKey(), commentString);
    }

    @Override
    public void updateProjectCustomFields(String projectKey, ProjectFieldUpdateRequest request) throws BusinessServiceException {
        Issue project = getProjectTicket(projectKey);

        if (project == null) {
            throw new BusinessServiceException(FAILED_TO_RECOVER_PROJECT_MSG + projectKey);
        }
        JSONObject magicTicketCustomFieldMap = new JSONObject();
        JSONObject projectFieldMap = new JSONObject();
        for (AuthoringProjectField field : request.fields()) {
            if (FIELD_NAME.equals(field.getName()) || FIELD_LEAD.equals(field.getName())) {
                projectFieldMap.put(field.getName(), field.getValue());
                continue;
            }

            if(STRING_TYPE.equals(field.getType())) {
                magicTicketCustomFieldMap.put(field.getId(), field.getValue());
            } else {
                JSONObject updateObj = new JSONObject();
                updateObj.put(VALUE, field.getValue());
                magicTicketCustomFieldMap.put(field.getId(), updateObj);
            }
        }
        JSONObject req = new JSONObject();
        req.put("fields", magicTicketCustomFieldMap);
        JiraClient jiraClient = jiraClientFactory.getAdminInstance();
        updateProjectMagicTicketCustomFields(project, jiraClient, req);
        updateProjectFields(projectKey, projectFieldMap, jiraClient);
    }

    private void updateProjectFields(String projectKey, JSONObject request, JiraClient jiraClient) throws BusinessServiceException {
        if (!request.isEmpty()) {
            try {
                JiraHelper.updateProject(jiraClient, projectKey, request);
            } catch (JiraException e) {
                throw new BusinessServiceException("Failed to update the project fields for project ." + projectKey, e);
            }
        }
    }

    private void updateProjectMagicTicketCustomFields(Issue project, JiraClient jiraClient, JSONObject request) throws BusinessServiceException {
        if (!request.isEmpty()) {
            try {
                URI uri = new URI(project.getSelf());
                jiraClient.getRestClient().put(uri, request);
            } catch (RestException | IOException | URISyntaxException e) {
                throw new BusinessServiceException("Failed to update SCA Project Magic ticket custom fields.", e);
            }
        }
    }

    private void addRolesToProject(Project projectTemplate, JiraClient adminJiraClient, String projectTemplateKey, Project newProject) throws BusinessServiceException {
        if (projectTemplate.getRoles().isEmpty()) {
            logger.warn("The template project {} does not contain any roles", projectTemplate.getKey());
            return;
        }

        for (Map.Entry<String, String> entry : projectTemplate.getRoles().entrySet()) {
            if (entry.getKey().equals("Administrators")) continue;

            String roleUrl = entry.getValue();
            String userRolesId = roleUrl.contains("/") ? roleUrl.substring(roleUrl.lastIndexOf("/") + 1) : roleUrl;
            Set<String> actorUsers;
            try {
                actorUsers = JiraHelper.getActorUsersFromProject(adminJiraClient, projectTemplateKey, userRolesId);
            } catch (JiraException e) {
                throw new BusinessServiceException("Failed to get roles from template project " + projectTemplate.getKey() + ". Error: " + e.getMessage(), e);
            }
            if (!actorUsers.isEmpty()) {
                try {
                    JiraHelper.addActorUsersToProject(adminJiraClient, newProject.getKey(), userRolesId, actorUsers);
                } catch (JiraException e) {
                    throw new BusinessServiceException("Failed to add roles to project " + newProject.getKey() + ". Error: " + e.getMessage(), e);
                }
            }
        }
    }

    private void createMagicTicketForProject(CreateProjectRequest request, String extensionBasePath) throws BusinessServiceException {
        try {
            JiraClient jiraClient = jiraClientFactory.getImpersonatingInstance(SecurityUtil.getUsername());
            Issue.FluentCreate fluentCreate = jiraClient.createIssue(request.key(), AUTHORING_PROJECT_TYPE);
            fluentCreate.field(Field.SUMMARY, request.name());
            fluentCreate.field(Field.DESCRIPTION, request.description());
            if (extensionBasePath != null) {
                fluentCreate.field(jiraExtensionBaseField, extensionBasePath);
            }
            fluentCreate.execute();
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to create Jira Magic ticket. Error: " + e.getMessage(), e);
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

        JiraClient jiraClient = getJiraClient();
        Future<Map<String, JiraProject>> unfilteredProjects = executorService.submit(() -> getProjects(jiraClient.getRestClient()).stream().collect(Collectors.toMap(JiraProject::key, Function.identity())));

        SecurityContext securityContext = SecurityContextHolder.getContext();
        projectTickets.parallelStream().forEach(projectTicket -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                final String projectKey = projectTicket.getProject().getKey();
                final String extensionBase = getProjectDetailsPopulatingCache(projectTicket).baseBranchPath();
                final String branchPath = PathHelper.getProjectPath(extensionBase, projectKey);


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
                Branch parentBranchOrNull = getParentBranch(parentBranchCache, parentPath);
                if (parentBranchOrNull == null) {
                    logger.error("Project {} expected parent branch does not exist: {}", projectKey, parentPath);
                    return;
                }
                parentBranchCache.put(parentPath, parentBranchOrNull);
                CodeSystem codeSystem = getCodeSystemForProject(codeSystems, parentPath);
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
                String latestClassificationJson = !lightweight ? classificationService.getLatestClassification(branchPath) : null;
                Map<String, JiraProject> projectMap = unfilteredProjects.get();
                JiraProject project = projectMap.get(projectKey);
                final AuthoringProject authoringProject = new AuthoringProject(projectKey, project.name(),
                        project.lead(), branchPath, branchState, baseTimeStamp, headTimeStamp, latestClassificationJson, promotionDisabled, mrcmDisabled, templatesDisabled, spellCheckDisabled, rebaseDisabled, scheduledRebaseDisabled, taskPromotionDisabled, projectLocked);
                authoringProject.setMetadata(metadata);
                authoringProject.setCodeSystem(codeSystem);
                synchronized (authoringProjects) {
                    authoringProjects.add(authoringProject);
                }
            } catch (RestClientException | ServiceException | ExecutionException e) {
                logger.error("Failed to fetch details of project {}", projectTicket.getProject().getName(), e);
            } catch (InterruptedException e) {
                logger.error("Interrupted!", e);
                Thread.currentThread().interrupt();
            }
        });

        populateValidationStatusForProjects(branchPaths, authoringProjects, lightweight);

        return authoringProjects;
    }

    private Branch getParentBranch(Map<String, Branch> parentBranchCache, String parentPath) throws ServiceException {
        Branch parentBranchOrNull = parentBranchCache.get(parentPath);
        if (parentBranchOrNull == null) {
            parentBranchOrNull = branchService.getBranchOrNull(parentPath);

        }
        return parentBranchOrNull;
    }

    @Nullable
    private static CodeSystem getCodeSystemForProject(List<CodeSystem> codeSystems, String parentPath) {
        CodeSystem codeSystem = codeSystems.stream().filter(c -> parentPath.equals(c.getBranchPath())).findFirst().orElse(null);
        if (codeSystem == null && parentPath.contains("/")) {
            // Attempt match using branch grandfather
            String grandfatherPath = PathHelper.getParentPath(parentPath);
            codeSystem = codeSystems.stream().filter(c -> grandfatherPath.equals(c.getBranchPath())).findFirst().orElse(null);
        }
        return codeSystem;
    }

    private void populateValidationStatusForProjects(Set<String> branchPaths, List<AuthoringProject> authoringProjects, boolean lightweight) {
        if (!lightweight) {
            final ImmutableMap<String, Validation> validationMap;
            try {
                validationMap = validationService.getValidations(branchPaths);
                for (AuthoringProject authoringProject : authoringProjects) {
                    populateValidationStatusForProject(authoringProject, validationMap);
                }
            } catch (ExecutionException e) {
                logger.warn("Failed to fetch validation statuses for branch paths {}", branchPaths);
            }
        }
    }

    private void populateValidationStatusForProject(AuthoringProject authoringProject, ImmutableMap<String, Validation> validationMap) {
        try {
            String branchPath = authoringProject.getBranchPath();
            Validation validation = validationMap.get(branchPath);
            if (validation != null) {
                if (ValidationJobStatus.COMPLETED.name().equals(validation.getStatus())
                        && validation.getContentHeadTimestamp() != null
                        && authoringProject.getBranchHeadTimestamp() != null
                        && !authoringProject.getBranchHeadTimestamp().equals(validation.getContentHeadTimestamp())) {
                    authoringProject.setValidationStatus(ValidationJobStatus.STALE.name());
                } else {
                    authoringProject.setValidationStatus(validation.getStatus());
                }
            }

        } catch (Exception e) {
            logger.error("Failed to recover/set validation status for " + authoringProject.getKey(), e);
        }
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

    private JiraClient getJiraClient() {
        return jiraClientFactory.getImpersonatingInstance(getUsername());
    }

    private String getUsername() {
        return SecurityUtil.getUsername();
    }

    private List<JiraProject> getProjects(RestClient restClient) throws JiraException {
        try {
            URI ex = restClient.buildURI(Resource.getBaseUri() + "project", Collections.singletonMap("expand", "lead"));
            JSON response = restClient.get(ex);
            JSONArray projectsArray = JSONArray.fromObject(response);
            ArrayList<JiraProject> projects = new ArrayList<>();

            for (int i = 0; i < projectsArray.size(); ++i) {
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
