package org.ihtsdo.authoringservices.domain;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;

public class AuthoringMain {

	private final String key;
	private final Classification latestClassification;
	private final String validationStatus;
	private String branchState;

	public AuthoringMain(String key, String branchState, String validationStatus, Classification latestClassification) {
		this.key = key;
		this.branchState = branchState;
		this.validationStatus = validationStatus;
		this.latestClassification = latestClassification;
	}

	public String getKey() {
		return key;
	}

	public String getValidationStatus() {
		return validationStatus;
	}

	public Classification getLatestClassification() {
		return latestClassification;
	}
	
	public String getBranchState() {
		return branchState;
	}

}
