package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.ihtsdo.sso.integration.SecurityUtil;

public final class ControllerHelper {

	private ControllerHelper() {
	}

	public static String getUsername() {
		return SecurityUtil.getUsername();
	}
}
