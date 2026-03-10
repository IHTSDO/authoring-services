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

    public static final String AP_REVIEWER_PATTERN = "ap-.*-reviewer";
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

    public boolean userHasPermissionOnProject(String projectKey) {
        List<String> loggedInUserRoles = getUserRoles();
        if (loggedInUserRoles.isEmpty()) return false;

        List<ProjectUserGroup> projectUserGroups = projectUserGroupRepository.findByNameIn(loggedInUserRoles);
        if (projectUserGroups.isEmpty()) return false;


        List<String> projectGroups = projectUserGroups.stream().filter(item -> item.getProject().getKey().equals(projectKey)).map(ProjectUserGroup::getName).toList();
        if (projectGroups.isEmpty()) return false;

        String reviewerRoleFromProject = projectGroups.stream().filter(item -> item.matches(AP_REVIEWER_PATTERN)).findFirst().orElse(null);
        if (reviewerRoleFromProject != null) {
            boolean foundReviewerRoleFromUser = loggedInUserRoles.contains(reviewerRoleFromProject);
            String codeSystem = reviewerRoleFromProject.split("-")[1];
            return !foundReviewerRoleFromUser || loggedInUserRoles.contains("ap-" + codeSystem + "-author");
        }
        return true;
    }

    public boolean userHasReviewerRoleOnProject(String projectKey) {
        List<String> loggedInUserRoles = getUserRoles();
        if (loggedInUserRoles.isEmpty()) return false;

        List<ProjectUserGroup> projectUserGroups = projectUserGroupRepository.findByNameIn(loggedInUserRoles);
        if (projectUserGroups.isEmpty()) return false;


        List<String> projectGroups = projectUserGroups.stream().filter(item -> item.getProject().getKey().equals(projectKey)).map(ProjectUserGroup::getName).toList();
        if (projectGroups.isEmpty()) return false;

        String reviewerRoleFromProject = projectGroups.stream().filter(item -> item.matches(AP_REVIEWER_PATTERN)).findFirst().orElse(null);
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

        List<Project> projects = projectUserGroups.stream().map(ProjectUserGroup::getProject).distinct().toList();

        projects.forEach(project -> {
            ProjectUserGroup reviewerRoleFromProject = projectUserGroups.stream().filter(item -> item.getProject().getKey().equals(project.getKey()) && item.getName().matches(AP_REVIEWER_PATTERN)).findFirst().orElse(null);
            if (reviewerRoleFromProject != null) {
                String codeSystem = reviewerRoleFromProject.getName().split("-")[1];
                if (!loggedInUserRoles.contains("ap-" + codeSystem + "-author")) {
                    project.setCanViewOnly(true);
                }
            }
        });

        return projects;
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
