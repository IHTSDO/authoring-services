package org.ihtsdo.snowowl.authoring.single.api.service;

import java.util.HashSet;
import java.util.Set;

public class InstanceConfiguration {

	private final Set<String> jiraProjectFilterProductCodes;

	public InstanceConfiguration(String codes) {
		jiraProjectFilterProductCodes = new HashSet<>();
		for (String code : codes.split(",")) {
			jiraProjectFilterProductCodes.add(code.trim().toLowerCase());
		}
	}

	public boolean isJiraProjectVisible(String productCode) {
		return jiraProjectFilterProductCodes.contains(productCode.toLowerCase());
	}
}
