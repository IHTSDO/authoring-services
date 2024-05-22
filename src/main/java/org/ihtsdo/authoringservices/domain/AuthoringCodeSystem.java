package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemVersion;

import java.util.List;
import java.util.Set;

@JsonDeserialize
public class AuthoringCodeSystem {
	private String shortName;
	private String name;
	private String countryCode;
	private String maintainerType;
	private String branchPath;
	private Set <String> userRoles;
	private Integer dependantVersionEffectiveTime;
	private CodeSystemVersion latestVersion;
	private String latestClassificationJson;
	private String latestValidationStatus;
	private List<CodeSystemVersion> versions;

	public AuthoringCodeSystem() {
	}

	public AuthoringCodeSystem(CodeSystem codeSystem) {
		setShortName(codeSystem.getShortName());
		setName(codeSystem.getName());
		setCountryCode(codeSystem.getCountryCode());
		setMaintainerType(codeSystem.getMaintainerType());
		setBranchPath(codeSystem.getBranchPath());
		setUserRoles(codeSystem.getUserRoles());
		setDependantVersionEffectiveTime(codeSystem.getDependantVersionEffectiveTime());
		setLatestVersion(codeSystem.getLatestVersion());
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getMaintainerType() {
		return maintainerType;
	}

	public void setMaintainerType(String maintainerType) {
		this.maintainerType = maintainerType;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public Set <String> getUserRoles() {
		return userRoles;
	}

	public void setUserRoles(Set <String> userRoles) {
		this.userRoles = userRoles;
	}

	public Integer getDependantVersionEffectiveTime() {
		return dependantVersionEffectiveTime;
	}

	public void setDependantVersionEffectiveTime(Integer dependantVersionEffectiveTime) {
		this.dependantVersionEffectiveTime = dependantVersionEffectiveTime;
	}

	public CodeSystemVersion getLatestVersion() {
		return latestVersion;
	}

	public void setLatestVersion(CodeSystemVersion latestVersion) {
		this.latestVersion = latestVersion;
	}

	public void setVersions(List<CodeSystemVersion> versions) {
		this.versions = versions;
	}

	public List<CodeSystemVersion> getVersions() {
		return versions;
	}

	@JsonRawValue
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}

	public void setLatestClassificationJson(String latestClassificationJson) {
		this.latestClassificationJson = latestClassificationJson;
	}

	public String getLatestValidationStatus() {
		return latestValidationStatus;
	}

	public void setLatestValidationStatus(String latestValidationStatus) {
		this.latestValidationStatus = latestValidationStatus;
	}
}
