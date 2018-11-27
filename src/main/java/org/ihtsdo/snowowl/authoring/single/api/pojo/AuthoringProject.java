package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

@JsonDeserialize
public class AuthoringProject {

	private String key;
	private String title;
	private User projectLead;
	private String branchPath;
	private String branchState;
	private String latestClassificationJson;
	private String validationStatus;
	private boolean projectPromotionDisabled;
	private boolean projectRebaseDisabled;
	private boolean projectMrcmDisabled;
	private boolean projectTemplatesDisabled;
	private boolean projectSpellCheckDisabled;
	private boolean projectScheduledRebaseDisabled;
	private Map<String, Object> metadata;

	public AuthoringProject() {
	}
	
	public AuthoringProject(String key, String title, User leadUser, String branchPath, String branchState,
							String latestClassificationJson, boolean projectPromotionDisabled,
							boolean projectMrcmDisabled, boolean projectTemplatesDisabled, boolean projectSpellCheckDisabled, boolean projectRebaseDisabled,
							boolean projectScheduledRebaseDisabled) {
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
		this.projectScheduledRebaseDisabled = projectScheduledRebaseDisabled;
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

	public boolean isProjectScheduledRebaseDisabled() {
		return projectScheduledRebaseDisabled;
	}

	public void setProjectScheduledRebaseDisabled(boolean projectScheduledRebaseDisabled) {
		this.projectScheduledRebaseDisabled = projectScheduledRebaseDisabled;
	}

	public void setProjectSpellCheckDisabled(boolean projectSpellCheckDisabled) {
		this.projectSpellCheckDisabled = projectSpellCheckDisabled;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
}
