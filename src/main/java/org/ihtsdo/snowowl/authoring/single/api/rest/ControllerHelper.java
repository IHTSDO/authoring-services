package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.ihtsdo.sso.integration.SecurityUtil;

public class ControllerHelper {

	public static String getUsername() {
		return SecurityUtil.getUsername();
	}
}
