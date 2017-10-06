package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.util.Map;

public class AuthoringProject {

	private final String key;
	private final String title;
	private final User projectLead;
	private final String branchPath;
	private String branchState;
	private String latestClassificationJson;
	private String validationStatus;
	private boolean projectPromotionDisabled;
	private boolean projectRebaseDisabled;
	private boolean projectMrcmDisabled;
	private boolean projectTemplatesDisabled;
	private boolean projectSpellCheckDisabled;
	private Map<String, Object> metadata;

	public AuthoringProject(String key, String title, User leadUser, String branchPath, String branchState,
							String latestClassificationJson, boolean projectPromotionDisabled,
							boolean projectMrcmDisabled, boolean projectTemplatesDisabled, boolean projectSpellCheckDisabled, boolean projectRebaseDisabled) {
		this.key = key;
		this.title = title;
		this.projectLead = leadUser;
		this.branchPath = branchPath;
		this.branchState = branchState;
		this.latestClassificationJson = latestClassificationJson;
		this.projectPromotionDisabled = projectPromotionDisabled;
		this.projectMrcmDisabled = projectMrcmDisabled;
		this.projectTemplatesDisabled = projectTemplatesDisabled;
		this.projectSpellCheckDisabled = projectSpellCheckDisabled;
		this.projectRebaseDisabled = projectRebaseDisabled;
	}

	public String getKey() {
		return key;
	}

	public String getTitle() {
		return title;
	}

	public User getProjectLead() {
		return projectLead;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public String getValidationStatus() {
		return validationStatus;
	}

	public void setValidationStatus(String validationStatus) {
		this.validationStatus = validationStatus;
	}

	@JsonRawValue
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}
	
	public String getBranchState() {
		return branchState;
	}

	public boolean isProjectPromotionDisabled() {
		return projectPromotionDisabled;
	}

	public void setProjectPromotionDisabled(boolean projectPromotionDisabled) {
		this.projectPromotionDisabled = projectPromotionDisabled;
	}
	
	public boolean isProjectRebaseDisabled() {
		return projectRebaseDisabled;
	}

	public void setProjectRebaseDisabled(boolean projectRebaseDisabled) {
		this.projectRebaseDisabled = projectRebaseDisabled;
	}

	public boolean isProjectMrcmDisabled() {
		return projectMrcmDisabled;
	}

	public void setProjectMrcmDisabled(boolean projectMrcmDisabled) {
		this.projectMrcmDisabled = projectMrcmDisabled;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public boolean isProjectTemplatesDisabled() {
		return projectTemplatesDisabled;
	}

	public void setProjectTemplatesDisabled(boolean projectTemplatesDisabled) {
		this.projectTemplatesDisabled = projectTemplatesDisabled;
	}

	public boolean isProjectSpellCheckDisabled() {
		return projectSpellCheckDisabled;
	}

	public void setProjectSpellCheckDisabled(boolean projectSpellCheckDisabled) {
		this.projectSpellCheckDisabled = projectSpellCheckDisabled;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
}
