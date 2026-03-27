package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.Project;
import org.ihtsdo.authoringservices.entity.ProjectUserGroup;
import org.ihtsdo.authoringservices.repository.ProjectUserGroupRepository;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final Logger logger = LoggerFactory.getLogger(PermissionService.class);

    public static final String GLOBAL_ROLE_SCOPE = "global";
    private static final String BRANCH_MAIN = "MAIN";

    private final SnowstormRestClientFactory snowstormRestClientFactory;
    private final ProjectUserGroupRepository projectUserGroupRepository;
    private final IMSClientFactory imsClientFactory;

    @Value("${authoring.reviewer.role.pattern}")
    private String reviewerRolePattern;

    @Autowired
    public PermissionService (SnowstormRestClientFactory snowstormRestClientFactory, ProjectUserGroupRepository projectUserGroupRepository, IMSClientFactory imsClientFactory) {
        this.snowstormRestClientFactory = snowstormRestClientFactory;
        this.projectUserGroupRepository = projectUserGroupRepository;
        this.imsClientFactory = imsClientFactory;
    }

    public boolean userHasRoleOnBranch(String role, String branchPath, Authentication authentication) throws RestClientException {
        Set<String> userRoleForBranch;
        if (GLOBAL_ROLE_SCOPE.equals(branchPath)) {
            userRoleForBranch = snowstormRestClientFactory.getClient().getBranch(BRANCH_MAIN).getGlobalUserRoles();
        } else {
            userRoleForBranch = snowstormRestClientFactory.getClient().getBranch(branchPath).getUserRoles();
        }
        boolean contains = userRoleForBranch.contains(role);
        if (!contains) {
            String username = getUsername(authentication);
            logger.info("User '{}' does not have required role '{}' on branch '{}', on this branch they have roles:{}.", username, role, branchPath, userRoleForBranch);
        }
        return contains;
    }

    public void checkUserPermissionOnProjectOrThrow(String projectKey) {
        if(!userHasPermissionOnProject(projectKey)) {
            throw new AccessDeniedException("User has no permission on project " + projectKey);
        }
    }

    public boolean userHasPermissionOnProject(String projectKey) {
        List<String> loggedInUserRoles = getUserRoles();
        List<String> projectGroups = findGroupsForProject(projectKey, loggedInUserRoles);
        if (projectGroups.isEmpty()) return false;

        List<String> intersection = projectGroups.stream()
                .filter(loggedInUserRoles::contains)
                .distinct()
                .toList();

        return !intersection.isEmpty() && (intersection.size() > 1 || !intersection.get(0).matches(reviewerRolePattern));
    }

    private List<String> findGroupsForProject(String projectKey, List<String> loggedInUserRoles) {
        if (loggedInUserRoles.isEmpty()) return Collections.emptyList();

        List<ProjectUserGroup> projectUserGroups = projectUserGroupRepository.findByNameIn(loggedInUserRoles);
        if (projectUserGroups.isEmpty()) return Collections.emptyList();


        List<String> projectGroups = projectUserGroups.stream().filter(item -> item.getProject().getKey().equals(projectKey)).map(ProjectUserGroup::getName).toList();
        if (projectGroups.isEmpty()) return Collections.emptyList();
        return projectGroups;
    }

    public boolean userHasReviewerRoleOnProject(String projectKey) {
        List<String> loggedInUserRoles = getUserRoles();
        List<String> projectGroups = findGroupsForProject(projectKey, loggedInUserRoles);
        if (projectGroups.isEmpty()) return false;

        String reviewerRoleFromProject = projectGroups.stream().filter(item -> item.matches(reviewerRolePattern)).findFirst().orElse(null);
        if (reviewerRoleFromProject != null) {
            return loggedInUserRoles.contains(reviewerRoleFromProject);
        }
        return false;
    }


    public List<Project> getProjectsForUser() {
        List<String> loggedInUserRoles = getUserRoles();
        if (loggedInUserRoles.isEmpty()) return Collections.emptyList();

        List<ProjectUserGroup> projectUserGroups = projectUserGroupRepository.findByNameIn(loggedInUserRoles);
        if (projectUserGroups.isEmpty()) return Collections.emptyList();
        Map<Project, List<ProjectUserGroup>> projectToGroupsMap = projectUserGroups.stream().collect(Collectors.groupingBy(ProjectUserGroup::getProject));
        projectToGroupsMap.forEach((key, value) -> {
            List<String> projectGroups = value.stream().map(ProjectUserGroup::getName).distinct().toList();
            key.setCanReviewTaskOnly(projectGroups.size() == 1 && projectGroups.get(0).matches(reviewerRolePattern));
        });

        return new ArrayList<>(projectToGroupsMap.keySet());
    }

    public List<String> getUserRoles() {
        User user = imsClientFactory.getClient().getLoggedInAccount();
        return user.getRoles();
    }

    private String getUsername(Authentication authentication) {
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal != null) {
                return principal.toString();
            }
        }
        return null;
    }
}
