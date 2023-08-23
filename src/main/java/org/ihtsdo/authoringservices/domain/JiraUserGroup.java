package org.ihtsdo.authoringservices.domain;

import java.util.List;
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

	public class UserGroupItem {
		private List <JiraUser> items;

		private int size;

		public List <JiraUser> getItems() {
			return items;
		}

		public void setItems(List <JiraUser> items) {
			this.items = items;
		}

		public int getSize() {
			return size;
		}

		public void setSize(int size) {
			this.size = size;
		}
	}
}
