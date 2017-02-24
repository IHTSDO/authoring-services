package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.annotation.JsonRawValue;

public class AuthoringMain {

	private final String key;
	private String latestClassificationJson;
	private final String validationStatus;
	private String branchState;

	public AuthoringMain(String key, String branchState, String validationStatus, String latestClassificationJson) {
		this.key = key;
		this.branchState = branchState;
		this.validationStatus = validationStatus;
		this.latestClassificationJson = latestClassificationJson;
	}

	public String getKey() {
		return key;
	}

	public String getValidationStatus() {
		return validationStatus;
	}

	@JsonRawValue
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}
	
	public String getBranchState() {
		return branchState;
	}

}
