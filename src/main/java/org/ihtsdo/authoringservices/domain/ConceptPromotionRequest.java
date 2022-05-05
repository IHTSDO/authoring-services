package org.ihtsdo.authoringservices.domain;

public class ConceptPromotionRequest {
	private String branchPath;
	private String projectKey;
	private String taskKey;
	private String conceptId;
	private boolean includeDependencies;

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public String getBranchPath() {
		return branchPath;
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

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setIncludeDependencies(boolean includeDependencies) {
		this.includeDependencies = includeDependencies;
	}

	public boolean isIncludeDependencies() {
		return includeDependencies;
	}
}
