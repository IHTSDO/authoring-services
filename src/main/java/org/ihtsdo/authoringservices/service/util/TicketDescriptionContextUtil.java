package org.ihtsdo.authoringservices.service.util;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;

public final class TicketDescriptionContextUtil {

	private TicketDescriptionContextUtil() {
		// Utility class
	}

	public static void appendEnvironmentAndUser(StringBuilder description, String platformUrl, Logger logger) {
		description.append("Environment: ").append(resolveEnvironment(platformUrl, logger)).append("\n");
		description.append("User: ").append(SecurityUtil.getUsername()).append("\n").append("\n");
	}

	private static String resolveEnvironment(String platformUrl, Logger logger) {
		final URI uri;
		try {
			uri = new URI(platformUrl);
		} catch (URISyntaxException e) {
			logger.error("Failed to detect environment", e);
			return null;
		}
		String domain = uri.getHost();
		domain = domain.startsWith("www.") ? domain.substring(4) : domain;
		return (domain.contains("-") ? domain.substring(0, domain.lastIndexOf("-")) : domain.substring(0, domain.indexOf("."))).toUpperCase();
	}
}
