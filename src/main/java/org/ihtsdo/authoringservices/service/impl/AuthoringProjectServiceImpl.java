package org.ihtsdo.authoringservices.service.impl;

import com.google.common.cache.LoadingCache;
import jakarta.transaction.Transactional;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.repository.TaskRepository;
import org.ihtsdo.authoringservices.service.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Transactional
public class AuthoringProjectServiceImpl extends ProjectServiceBase implements ProjectService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String PROJECT_LOCKED_FILED = "projectLocked";

    private static final String ENABLED_TEXT = "Enabled";
    private static final String DISABLED_TEXT = "Disabled";

    @Autowired
    private ProjectCustomFieldConfiguration projectCustomFieldConfiguration;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Autowired
    private SnowstormClassificationClient classificationService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    @Qualifier(value = "authoringTaskService")
    private TaskService authoringTaskService;

    @Override
    public boolean isUseNew(String projectKey) {
        Optional<Project> projectOptional = projectRepository.findById(projectKey);
        return projectOptional.isPresent();
    }

    @Override
    public AuthoringProject createProject(CreateProjectRequest request, String codeSystemBranchPath) throws BusinessServiceException {
        Project project = new Project();
        project.setKey(request.key());
        project.setName(request.name());
        project.setLead(request.lead());
        project.setBranchPath(codeSystemBranchPath + "/" + request.key());
        project.setExtensionBase(codeSystemBranchPath);
        project.setCreatedDate(Timestamp.from(Instant.now()));
        project.setUpdatedDate(Timestamp.from(Instant.now()));
        project = projectRepository.save(project);

        try {
            branchService.createBranchIfNeeded(project.getBranchPath());
        } catch (ServiceException e) {
            throw new BusinessServiceException("Failed to create project branch.", e);
        }
        return buildAuthoringProjects(List.of(project), true).get(0);
    }

    @Override
    public AuthoringProject updateProject(String projectKey, AuthoringProject updatedProject) throws BusinessServiceException {
        Project project = getProjectOrThrow(projectKey);
        Map<String, Boolean> customFields = Optional.ofNullable(project.getCustomFields()).orElse(new HashMap<>());
        if (updatedProject.isTaskPromotionDisabled() != null) {
            customFields.put("taskPromotion", !updatedProject.isTaskPromotionDisabled());
        }
        if (updatedProject.isProjectScheduledRebaseDisabled() != null) {
            customFields.put("projectScheduledRebase", !updatedProject.isProjectScheduledRebaseDisabled());
        }
        project.setUpdatedDate(Timestamp.from(Instant.now()));
        projectRepository.save(project);
        return buildAuthoringProjects(List.of(project), false).get(0);
    }

    @Override
    public void deleteProject(String projectKey) throws BusinessServiceException {
        Project project = getProjectOrThrow(projectKey);
        taskRepository.deleteAll(taskRepository.findByProject(project));
        projectRepository.delete(project);
    }

    @Override
    public List<AuthoringProjectField> retrieveProjectCustomFields(String projectKey) throws BusinessServiceException {
        Project project = getProjectOrThrow(projectKey);
        Map<String, Boolean> customFields = Optional.ofNullable(project.getCustomFields()).orElse(new HashMap<>());
        List<AuthoringProjectField> result = new ArrayList<>();
        projectCustomFieldConfiguration.getCustomFields().forEach((key, value) -> {
            AuthoringProjectField field = new AuthoringProjectField();
            field.setId(key);
            field.setName(value);
            field.setType("option");

            if (customFields.containsKey(key)) {
                field.setValue(Boolean.TRUE.equals(customFields.get(key)) ? ENABLED_TEXT : DISABLED_TEXT);
            }

            result.add(field);
        });
        return result;
    }

    @Override
    public List<AuthoringProject> listProjects(Boolean lightweight, Boolean ignoreProductCodeFilter) {
        Iterable<Project> projects = projectRepository.findAll();
        List<Project> result = new ArrayList<>();
        projects.forEach(result::add);
        return buildAuthoringProjects(result, lightweight);
    }

    @Override
    public AuthoringProject retrieveProject(String projectKey) {
        Optional<Project> projectOptional = projectRepository.findById(projectKey);
        return projectOptional.map(project -> buildAuthoringProjects(List.of(project), false).get(0)).orElse(null);

    }

    @Override
    public AuthoringProject retrieveProject(String projectKey, boolean lightweight) {
        Optional<Project> projectOptional = projectRepository.findById(projectKey);
        return projectOptional.map(project -> buildAuthoringProjects(List.of(project), lightweight).get(0)).orElse(null);
    }

    @Override
    public void lockProject(String projectKey) throws BusinessServiceException {
        Project project = getProjectOrThrow(projectKey);
        Map<String, Boolean> customFields = Optional.ofNullable(project.getCustomFields()).orElse(new HashMap<>());
        customFields.put(PROJECT_LOCKED_FILED, true);
        project.setCustomFields(customFields);
        project.setUpdatedDate(Timestamp.from(Instant.now()));
        projectRepository.save(project);
    }

    @Override
    public void unlockProject(String projectKey) throws BusinessServiceException {
        Project project = getProjectOrThrow(projectKey);
        Map<String, Boolean> customFields = Optional.ofNullable(project.getCustomFields()).orElse(new HashMap<>());
        customFields.put(PROJECT_LOCKED_FILED, false);
        project.setCustomFields(customFields);
        project.setUpdatedDate(Timestamp.from(Instant.now()));
        projectRepository.save(project);
    }

    @Override
    public String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException {
        Project project = getProjectOrThrow(projectKey);
        return project.getExtensionBase();
    }

    @Override
    public LoadingCache<String, ProjectDetails> getProjectDetailsCache() {
        return null;
    }

    @Override
    public void addCommentLogErrors(String projectKey, String commentString) {
        // Do nothing
    }

    @Override
    public void updateProjectCustomFields(String projectKey, ProjectFieldUpdateRequest request) throws BusinessServiceException {
        Project project = getProjectOrThrow(projectKey);
        Map<String, Boolean> customFields = Optional.ofNullable(project.getCustomFields()).orElse(new HashMap<>());
        for (AuthoringProjectField item : request.fields()) {
            customFields.put(item.getId(), ENABLED_TEXT.equals(item.getValue()));
        }
        project.setCustomFields(customFields);
        project.setUpdatedDate(Timestamp.from(Instant.now()));
        projectRepository.save(project);
    }

    @NotNull
    private Project getProjectOrThrow(String projectKey) throws BusinessServiceException {
        Optional<Project> projectOptional = projectRepository.findById(projectKey);
        if (projectOptional.isEmpty()) {
            throw new BusinessServiceException("Project with key " + projectKey + " not found");
        }
        return projectOptional.get();
    }

    @Override
    protected List<AuthoringProject> buildAuthoringProjects(Collection<?> collection, Boolean lightweight) {
        if (collection.isEmpty()) {
            return new ArrayList<>();
        }
        List<Project> projects = (List<Project>) collection;
        final List<AuthoringProject> authoringProjects = new ArrayList<>();
        final Set<String> branchPaths = new HashSet<>();
        final Map<String, Branch> parentBranchCache = new ConcurrentHashMap<>();
        final SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
        List<CodeSystem> codeSystems = snowstormRestClient.getCodeSystems();

        SecurityContext securityContext = SecurityContextHolder.getContext();
        projects.parallelStream().forEach(projectTicket -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                final String projectKey = projectTicket.getKey();
                final String branchPath = projectTicket.getBranchPath();

                Map<String, Boolean> customFields = Optional.ofNullable(projectTicket.getCustomFields()).orElse(new HashMap<>());
                final boolean promotionDisabled = !Boolean.TRUE.equals(customFields.get("projectPromotion"));
                final boolean projectLocked = Boolean.TRUE.equals(customFields.get(PROJECT_LOCKED_FILED));
                final boolean taskPromotionDisabled = !Boolean.TRUE.equals(customFields.get("taskPromotion"));
                final boolean rebaseDisabled = !Boolean.TRUE.equals(customFields.get("projectRebase"));
                final boolean scheduledRebaseDisabled = !Boolean.TRUE.equals(customFields.get("projectScheduledRebase"));
                final boolean mrcmDisabled = !Boolean.TRUE.equals(customFields.get("projectMrcm"));
                final boolean templatesDisabled = !Boolean.TRUE.equals(customFields.get("projectTemplates"));
                final boolean spellCheckDisabled = !Boolean.TRUE.equals(customFields.get("projectSpellCheck"));

                final Branch branchOrNull = branchService.getBranchOrNull(branchPath);
                String parentPath = PathHelper.getParentPath(branchPath);
                Branch parentBranchOrNull = getParentBranch(branchService, parentBranchCache, parentPath);
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
                String latestClassificationJson = !Boolean.TRUE.equals(lightweight) ? classificationService.getLatestClassification(branchPath) : null;

                User lead = authoringTaskService.getUser(projectTicket.getLead());
                final AuthoringProject authoringProject = new AuthoringProject(projectKey, projectTicket.getName(),
                        lead, branchPath, branchState, baseTimeStamp, headTimeStamp, latestClassificationJson, promotionDisabled, mrcmDisabled, templatesDisabled, spellCheckDisabled, rebaseDisabled, scheduledRebaseDisabled, taskPromotionDisabled, projectLocked);
                authoringProject.setMetadata(metadata);
                authoringProject.setCodeSystem(codeSystem);
                synchronized (authoringProjects) {
                    authoringProjects.add(authoringProject);
                }
            } catch (RestClientException | ServiceException | BusinessServiceException e) {
                logger.error("Failed to fetch details of project {}", projectTicket.getName(), e);
            }
        });

        populateValidationStatusForProjects(validationService, branchPaths, authoringProjects, lightweight);
        return authoringProjects;
    }
}
