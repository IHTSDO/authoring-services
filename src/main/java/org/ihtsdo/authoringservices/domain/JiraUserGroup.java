package org.ihtsdo.authoringservices.domain;

public class JiraUserGroup {
	private String expand;

	private String name;

	private UserGroupItem users;

	public String getExpand() {
		return expand;
	}

	public void setExpand(String expand) {
		this.expand = expand;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public UserGroupItem getUsers() {
		return users;
	}

	public void setUsers(UserGroupItem users) {
		this.users = users;
	}
}
