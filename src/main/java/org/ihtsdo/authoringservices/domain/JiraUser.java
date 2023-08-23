package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

public class JiraUser {
	private boolean active;

	private Map <String, String> avatarUrls;

	private String displayName;

	private String name;

	private String key;

	private String emailAddress;

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Map <String, String> getAvatarUrls() {
		return avatarUrls;
	}

	public void setAvatarUrls(Map <String, String> avatarUrls) {
		this.avatarUrls = avatarUrls;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	@JsonIgnore
	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}
}
