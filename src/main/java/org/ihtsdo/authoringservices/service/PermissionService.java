package org.ihtsdo.authoringservices.service;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PermissionService {

    private final Logger logger = LoggerFactory.getLogger(PermissionService.class);

    public static final String GLOBAL_ROLE_SCOPE = "global";
    private static final String BRANCH_MAIN = "MAIN";

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

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
