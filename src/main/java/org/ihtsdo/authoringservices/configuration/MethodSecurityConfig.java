package org.ihtsdo.authoringservices.configuration;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.ihtsdo.authoringservices.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

	@Bean
	protected MethodSecurityExpressionHandler methodSecurityExpressionHandler(@Autowired PermissionEvaluator permissionEvaluator) {
		DefaultMethodSecurityExpressionHandler expressionHandler =
				new DefaultMethodSecurityExpressionHandler();
		expressionHandler.setPermissionEvaluator(permissionEvaluator);
		return expressionHandler;
	}

	@Bean
	public PermissionEvaluator permissionEvaluator(@Lazy PermissionService permissionService) {
		return new PermissionEvaluator() {
			@Override
			public boolean hasPermission(Authentication authentication, Object role, Object branchObject) {
				if (branchObject == null) {
					throw new SecurityException("Branch path is null, can not ascertain roles.");
				}
                try {
                    return permissionService.userHasRoleOnBranch((String) role, BranchPathUriUtil.decodePath((String) branchObject), authentication);
                } catch (Exception e) {
					throw new SecurityException("Failed to determine role.", e);
                }
            }

			@Override
			public boolean hasPermission(Authentication authentication, Serializable serializable, String s, Object o) {
				return false;
			}
		};
	}
}

