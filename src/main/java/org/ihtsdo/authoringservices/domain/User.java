package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.sf.json.JSONObject;

import java.util.List;
import java.util.Objects;

public class User {
	
	private static final String JSON_FIELD_EMAIL = "emailAddress";
	private static final String JSON_FIELD_DISPLAY_NAME = "displayName";
	private static final String JSON_FIELD_NAME = "name";
	private static final String JSON_FIELD_AVATAR = "avatarUrls";
	private static final String JSON_FIELD_AVATAR_48 = "48x48";

	private String email;
	private String displayName;
	private String username;
	private String avatarUrl;

	private boolean active;

	private List<String> roles;

	public User() {

	}

	public User(net.rcarz.jiraclient.User assignee) {
		email = assignee.getEmail();
		displayName = assignee.getDisplayName();
		username = assignee.getName();
		avatarUrl = assignee.getAvatarUrls().get(JSON_FIELD_AVATAR_48);
	}

	public User(JSONObject userJSON) {
		if (userJSON.containsKey(JSON_FIELD_EMAIL)) {
			email = userJSON.getString(JSON_FIELD_EMAIL);
		}
		username = userJSON.getString(JSON_FIELD_NAME);
		displayName = userJSON.getString(JSON_FIELD_DISPLAY_NAME);
		JSONObject avatarUrls = userJSON.getJSONObject(JSON_FIELD_AVATAR);
		avatarUrl = avatarUrls.getString(JSON_FIELD_AVATAR_48);
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public void setAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	@JsonIgnore
	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof User user) {
			String otherUsername = user.getUsername();
			return this.username.equals(otherUsername);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(username);
	}
}
