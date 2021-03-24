package org.ihtsdo.authoringservices.domain;

public class JiraProject {

	private final String key;
	private final String name;
	private final User lead;

	public JiraProject(String key, String name, User lead) {
		this.key = key;
		this.name = name;
		this.lead = lead;
	}

	public String getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public User getLead() {
		return lead;
	}
}
