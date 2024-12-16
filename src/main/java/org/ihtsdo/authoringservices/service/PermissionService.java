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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class PermissionService {

    private final Logger logger = LoggerFactory.getLogger(PermissionService.class);

    public static final String GLOBAL_ROLE_SCOPE = "global";
    private static final String BRANCH_MAIN = "MAIN";

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Autowired
    private ProjectUserGroupRepository projectUserGroupRepository;

    @Autowired
    private IMSClientFactory imsClientFactory;

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

    public List<Project> getProjectsForUser() {
        List<String> loggedInUserRoles = getUserRoles();
        if (loggedInUserRoles.isEmpty()) return Collections.emptyList();

        List<ProjectUserGroup> projectUserGroups = projectUserGroupRepository.findByNameIn(loggedInUserRoles);
        if (projectUserGroups.isEmpty()) return Collections.emptyList();

        return projectUserGroups.stream().map(ProjectUserGroup::getProject).distinct().toList();
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

    private boolean userHasPermissionOnProject(String projectKey) {
        List<String> loggedInUserRoles = getUserRoles();
        if (loggedInUserRoles.isEmpty()) return false;

        List<ProjectUserGroup> projectUserGroups = projectUserGroupRepository.findByNameIn(loggedInUserRoles);
        if (projectUserGroups.isEmpty()) return false;

        return projectUserGroups.stream().anyMatch(item -> item.getProject().getKey().equals(projectKey));
    }

}
