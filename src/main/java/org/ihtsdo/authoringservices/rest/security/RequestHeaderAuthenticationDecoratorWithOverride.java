package org.ihtsdo.authoringservices.rest.security;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class RequestHeaderAuthenticationDecoratorWithOverride extends org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator {
	private final String overrideUsername;
	private final String overrideRoles;
	private final String overrideToken;

	private static final Logger logger = LoggerFactory.getLogger(RequestHeaderAuthenticationDecoratorWithOverride.class);

	public RequestHeaderAuthenticationDecoratorWithOverride(String overrideUsername, String overrideRoles, String overrideToken) {
		this.overrideUsername = overrideUsername;
		this.overrideRoles = overrideRoles;
		this.overrideToken = overrideToken;
	}

	@Override
	protected String getUsername(HttpServletRequest request) {
		if (!Strings.isNullOrEmpty(overrideUsername)) {
			logger.warn("Using authentication override username {}", overrideUsername);
			return overrideUsername;
		} else {
			return super.getUsername(request);
		}
	}

	@Override
	protected String getRoles(HttpServletRequest request) {
		if (!Strings.isNullOrEmpty(overrideRoles)) {
			logger.warn("Using authentication override roles {}", overrideRoles);
			return overrideRoles;
		} else {
			return super.getRoles(request);
		}
	}

	@Override
	protected String getToken(HttpServletRequest request) {
		if (!Strings.isNullOrEmpty(overrideToken)) {
			int visibleChars = Math.min(6, overrideToken.length());
			String tokenSuffix = overrideToken.substring(overrideToken.length() - visibleChars);
			logger.warn("Using authentication override token that ends with: {}", tokenSuffix);
			return overrideToken;
		} else {
			return super.getToken(request);
		}
	}
}

