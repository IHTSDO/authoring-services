package org.ihtsdo.authoringservices.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class InstanceConfiguration {

	private final Set<String> jiraProjectFilterProductCodes;

	public InstanceConfiguration(@Value("${jiraProjectFilterProductCodes}") String codes) {
		jiraProjectFilterProductCodes = new HashSet<>();
		for (String code : codes.split(",")) {
			jiraProjectFilterProductCodes.add(code.trim().toLowerCase());
		}
	}

	public boolean isJiraProjectVisible(String productCode) {
		return jiraProjectFilterProductCodes.contains(productCode.toLowerCase());
	}
}
