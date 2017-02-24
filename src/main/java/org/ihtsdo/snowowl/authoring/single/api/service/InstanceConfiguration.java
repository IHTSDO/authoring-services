package org.ihtsdo.snowowl.authoring.single.api.service;

import java.util.HashSet;
import java.util.Set;

public class InstanceConfiguration {

	private Set<String> jiraProjectFilterProductCodes;

	public InstanceConfiguration(Set<String> jiraProjectFilterProductCodes) {
		final Set<String> lowerCodes = new HashSet<>();
		for (String code : jiraProjectFilterProductCodes) {
			lowerCodes.add(code.toLowerCase());
		}
		this.jiraProjectFilterProductCodes = lowerCodes;
	}

	public boolean isJiraProjectVisible(String productCode) {
		return jiraProjectFilterProductCodes.contains(productCode.toLowerCase());
	}
}
