package org.ihtsdo.authoringservices.service.impl;

import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.ihtsdo.authoringservices.domain.ProjectCustomFieldConfiguration;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.repository.ProjectRepository;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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

    private static final Set<String> DISABLED_BY_DEFAULT = Set.of(
            PROJECT_LOCKED_FIELD,
            TRANSLATION_PROJECT_FIELD
    );

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new AuthoringProjectServiceImpl());
        when(projectCustomFieldConfiguration.getCustomFieldsDisabledByDefault()).thenReturn(DISABLED_BY_DEFAULT);
        ReflectionTestUtils.setField(service, "projectCustomFieldConfiguration", projectCustomFieldConfiguration);
        ReflectionTestUtils.setField(service, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(service, "snowstormRestClientFactory", snowstormRestClientFactory);
        ReflectionTestUtils.setField(service, "branchService", branchService);
        ReflectionTestUtils.setField(service, "requiredRbacGroups", List.of("ROLE_AUTHOR"));
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
        verify(branchService).createBranchIfNeeded("MAIN/SNOMEDCT/TEST");
    }
}
