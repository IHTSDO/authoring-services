package org.ihtsdo.authoringservices.service.impl;

import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.AuthoringProjectField;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.ihtsdo.authoringservices.domain.ProjectCustomFieldConfiguration;
import org.ihtsdo.authoringservices.domain.ProjectFieldUpdateRequest;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.PermissionService;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.PermissionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.ihtsdo.authoringservices.service.impl.AuthoringProjectServiceImpl.PROJECT_LOCKED_FIELD;

@ExtendWith(MockitoExtension.class)
class AuthoringProjectServiceImplTest {

    private static final String TRANSLATION_PROJECT_FIELD = "translationProject";

    private AuthoringProjectServiceImpl service;

    @Mock
    private ProjectCustomFieldConfiguration projectCustomFieldConfiguration;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Mock
    private SnowstormRestClient snowstormRestClient;

    @Mock
    private BranchService branchService;

    @Mock
    private PermissionService permissionService;

    private static final Set<String> DISABLED_BY_DEFAULT = Set.of(
            PROJECT_LOCKED_FIELD,
            TRANSLATION_PROJECT_FIELD
    );

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new AuthoringProjectServiceImpl());
        lenient().when(projectCustomFieldConfiguration.getCustomFieldsDisabledByDefault()).thenReturn(DISABLED_BY_DEFAULT);
        ReflectionTestUtils.setField(service, "projectCustomFieldConfiguration", projectCustomFieldConfiguration);
        ReflectionTestUtils.setField(service, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(service, "snowstormRestClientFactory", snowstormRestClientFactory);
        ReflectionTestUtils.setField(service, "branchService", branchService);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        ReflectionTestUtils.setField(service, "requiredRbacGroups", List.of("ROLE_AUTHOR"));
        ReflectionTestUtils.setField(service, "defaultAdminRoles", List.of());
        ReflectionTestUtils.setField(service, "jiraEnabled", false);
    }

    @Test
    void buildDefaultCustomFields_enablesAllFieldsExceptProjectLockedAndTranslationProject() {
        when(projectCustomFieldConfiguration.getCustomFields()).thenReturn(Map.of(
                "projectPromotion", "Project Promotion",
                PROJECT_LOCKED_FIELD, "Project Locked",
                TRANSLATION_PROJECT_FIELD, "Translation Project",
                "projectMrcm", "MRCM"
        ));

        Map<String, Boolean> result = service.buildDefaultCustomFields();

        assertEquals(Map.of(
                "projectPromotion", true,
                PROJECT_LOCKED_FIELD, false,
                TRANSLATION_PROJECT_FIELD, false,
                "projectMrcm", true
        ), result);
    }

    @Test
    void buildDefaultCustomFields_returnsEmptyMapWhenNoFieldsConfigured() {
        when(projectCustomFieldConfiguration.getCustomFields()).thenReturn(Map.of());

        Map<String, Boolean> result = service.buildDefaultCustomFields();

        assertTrue(result.isEmpty());
    }

    @Test
    void createProject_setsDefaultCustomFieldsOnSavedProject() throws Exception {
        when(projectCustomFieldConfiguration.getCustomFields()).thenReturn(Map.of(
                "projectPromotion", "Project Promotion",
                PROJECT_LOCKED_FIELD, "Project Locked",
                TRANSLATION_PROJECT_FIELD, "Translation Project"
        ));
        when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
        when(snowstormRestClient.findPermissionForBranch("MAIN/SNOMEDCT")).thenReturn(List.of());

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        when(projectRepository.save(projectCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        AuthoringCodeSystem codeSystem = new AuthoringCodeSystem();
        codeSystem.setBranchPath("MAIN/SNOMEDCT");
        CreateProjectRequest request = new CreateProjectRequest("SNOMEDCT", "TEST", "Test Project", "testuser", null, null);

        doReturn(List.of(new AuthoringProject())).when(service).buildAuthoringProjects(any(), eq(true));

        service.createProject(request, codeSystem);

        Map<String, Boolean> savedCustomFields = projectCaptor.getValue().getCustomFields();
        assertTrue(savedCustomFields.get("projectPromotion"));
        assertFalse(savedCustomFields.get(PROJECT_LOCKED_FIELD));
        assertFalse(savedCustomFields.get(TRANSLATION_PROJECT_FIELD));
    }

    @Test
    void createProject_addsConfiguredDefaultAdminRole() throws Exception {
        ReflectionTestUtils.setField(service, "defaultAdminRoles", List.of(" default-project-admin ", "default-project-lead", ""));
        when(projectCustomFieldConfiguration.getCustomFields()).thenReturn(Map.of());
        when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
        when(snowstormRestClient.findPermissionForBranch("MAIN/SNOMEDCT")).thenReturn(List.of());
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(List.of(new AuthoringProject())).when(service).buildAuthoringProjects(any(), eq(true));

        service.createProject(projectRequest(), codeSystem());

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        assertEquals(Set.of("default-project-admin", "default-project-lead"), groupNames(projectCaptor.getValue()));
    }

    @Test
    void createProject_doesNotDuplicateDefaultAdminRoleAlreadyCopiedFromCodeSystem() throws Exception {
        ReflectionTestUtils.setField(service, "requiredRbacGroups", List.of("ADMIN"));
        ReflectionTestUtils.setField(service, "defaultAdminRoles", List.of("default-project-admin", "default-project-lead"));
        when(projectCustomFieldConfiguration.getCustomFields()).thenReturn(Map.of());
        when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
        PermissionRecord admin = new PermissionRecord();
        admin.setRole("ADMIN");
        admin.setUserGroups(Set.of("default-project-admin", "extension-admins"));
        when(snowstormRestClient.findPermissionForBranch("MAIN/SNOMEDCT")).thenReturn(List.of(admin));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(List.of(new AuthoringProject())).when(service).buildAuthoringProjects(any(), eq(true));

        service.createProject(projectRequest(), codeSystem());

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        assertEquals(Set.of("default-project-admin", "default-project-lead", "extension-admins"), groupNames(projectCaptor.getValue()));
    }

    @Test
    void listProjects_excludesReviewerOnlyProjectsByDefault() {
        Project authorProject = project("AUTH", true, false);
        Project reviewerProject = project("REV", true, true);
        Project inactiveProject = project("OLD", false, false);
        when(permissionService.getProjectsForUser()).thenReturn(List.of(authorProject, reviewerProject, inactiveProject));
        ArgumentCaptor<Collection<?>> projectsCaptor = collectionCaptor();
        doReturn(List.of()).when(service).buildAuthoringProjects(projectsCaptor.capture(), eq(true));

        service.listProjects(true, null, null);

        assertEquals(Set.of("AUTH"), keys(projectsCaptor.getValue()));
    }

    @Test
    void listProjects_includesReviewerOnlyProjectsWhenRequested() {
        Project authorProject = project("AUTH", true, false);
        Project reviewerProject = project("REV", true, true);
        Project inactiveReviewerProject = project("OLD", false, true);
        when(permissionService.getProjectsForUser()).thenReturn(List.of(authorProject, reviewerProject, inactiveReviewerProject));
        ArgumentCaptor<Collection<?>> projectsCaptor = collectionCaptor();
        doReturn(List.of()).when(service).buildAuthoringProjects(projectsCaptor.capture(), eq(true));

        service.listProjects(true, null, null, true);

        assertEquals(Set.of("AUTH", "REV"), keys(projectsCaptor.getValue()));
    }

    @Test
    void updateProjectCustomFields_setsTranslationLabelWhenEnabled() throws Exception {
        Project project = projectEntity("TEST", "MAIN/SNOMEDCT/TEST");
        when(projectRepository.findById("TEST")).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateProjectCustomFields("TEST", fieldRequest("translationProject", "Enabled"));

        verify(branchService).setTranslationLabel("MAIN/SNOMEDCT/TEST", true);
    }

    @Test
    void updateProjectCustomFields_clearsTranslationLabelWhenDisabled() throws Exception {
        Project project = projectEntity("TEST", "MAIN/SNOMEDCT/TEST");
        when(projectRepository.findById("TEST")).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateProjectCustomFields("TEST", fieldRequest("translationProject", "Disabled"));

        verify(branchService).setTranslationLabel("MAIN/SNOMEDCT/TEST", false);
    }

    @Test
    void updateProjectCustomFields_doesNotCallSetTranslationLabelForUnrelatedField() throws Exception {
        Project project = projectEntity("TEST", "MAIN/SNOMEDCT/TEST");
        when(projectRepository.findById("TEST")).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateProjectCustomFields("TEST", fieldRequest("projectPromotion", "Enabled"));

        verify(branchService, never()).setTranslationLabel(any(), any(Boolean.class));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<?>> collectionCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    private static Project project(String key, boolean active, boolean reviewerOnly) {
        Project project = new Project();
        project.setKey(key);
        project.setActive(active);
        project.setCanReviewTaskOnly(reviewerOnly);
        return project;
    }

    private static Set<String> keys(Collection<?> projects) {
        return projects.stream().map(Project.class::cast).map(Project::getKey).collect(Collectors.toSet());
    }

    private static AuthoringCodeSystem codeSystem() {
        AuthoringCodeSystem codeSystem = new AuthoringCodeSystem();
        codeSystem.setBranchPath("MAIN/SNOMEDCT");
        return codeSystem;
    }

    private static CreateProjectRequest projectRequest() {
        return new CreateProjectRequest("SNOMEDCT", "TEST", "Test Project", "testuser", null, null);
    }

    private static Set<String> groupNames(Project project) {
        return project.getUserGroups().stream().map(group -> group.getName()).collect(Collectors.toSet());
    }

    private static Project projectEntity(String key, String branchPath) {
        Project project = new Project();
        project.setKey(key);
        project.setBranchPath(branchPath);
        return project;
    }

    private static ProjectFieldUpdateRequest fieldRequest(String id, String value) {
        AuthoringProjectField field = new AuthoringProjectField();
        field.setId(id);
        field.setValue(value);
        return new ProjectFieldUpdateRequest(List.of(field));
    }
}
