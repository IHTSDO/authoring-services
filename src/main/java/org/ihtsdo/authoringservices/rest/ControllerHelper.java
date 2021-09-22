package org.ihtsdo.authoringservices.rest;

import com.google.common.base.Strings;

class ControllerHelper {

	public static final String PROJECT_KEY = "projectKey";
	public static final String TASK_KEY = "taskKey";

	private ControllerHelper() {
	}

	// This request value commonly comes from a Javascript application that has event sequencing issues.
	public static final String UNDEFINED = "undefined";

	public static String requiredParam(String value, String paramName) {
		if (Strings.isNullOrEmpty(value) || UNDEFINED.equals(value)) {
			throw new IllegalArgumentException(String.format("Parameter %s is required.", paramName));
		}
		return value;
	}
}
