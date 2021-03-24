package org.ihtsdo.authoringservices.domain;

import org.springframework.security.core.Authentication;

public class AutomatePromoteProcess {
	
	private Authentication authentication;
	private String projectKey;
	private String taskKey;

	public AutomatePromoteProcess() {
	}

	public AutomatePromoteProcess(Authentication authentication, String projectKey, String taskKey) {
		this.authentication = authentication;
		this.projectKey = projectKey;
		this.taskKey = taskKey;
	}

	public Authentication getAuthentication() {
		return authentication;
	}
	
	public void setAuthentication(Authentication authentication) {
		this.authentication = authentication;
	}
	
	public String getProjectKey() {
		return projectKey;
	}
	
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}
	
	public String getTaskKey() {
		return taskKey;
	}
	
	public void setTaskKey(String taskKey) {
		this.taskKey = taskKey;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((authentication == null) ? 0 : authentication.hashCode());
		result = prime * result + ((projectKey == null) ? 0 : projectKey.hashCode());
		result = prime * result + ((taskKey == null) ? 0 : taskKey.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AutomatePromoteProcess other = (AutomatePromoteProcess) obj;
		if (authentication == null) {
			if (other.authentication != null)
				return false;
		} else if (!authentication.equals(other.authentication))
			return false;
		if (projectKey == null) {
			if (other.projectKey != null)
				return false;
		} else if (!projectKey.equals(other.projectKey))
			return false;
		if (taskKey == null) {
			if (other.taskKey != null)
				return false;
		} else if (!taskKey.equals(other.taskKey))
			return false;
		return true;
	}
	
}