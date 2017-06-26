package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.browser;

import java.util.Map;

public class SnomedBrowserDescription implements SnomedBrowserConstants {

	String descriptionId;
	String term;
	boolean active;
	DescriptionType type;
	String lang;
	Map<String, Acceptability> acceptabilityMap;
	CaseSignificance caseSignificance;
	
	public String getDescriptionId() {
		return descriptionId;
	}
	public void setDescriptionId(String descriptionId) {
		this.descriptionId = descriptionId;
	}
	public String getTerm() {
		return term;
	}
	public void setTerm(String term) {
		this.term = term;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public DescriptionType getType() {
		return type;
	}
	public void setType(DescriptionType type) {
		this.type = type;
	}
	public String getLang() {
		return lang;
	}
	public void setLang(String lang) {
		this.lang = lang;
	}
	public Map<String, Acceptability> getAcceptabilityMap() {
		return acceptabilityMap;
	}
	public void setAcceptabilityMap (Map<String, Acceptability> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
	}
	public CaseSignificance getCaseSignificance() {
		return caseSignificance;
	}
	public void setCaseSignificance(CaseSignificance caseSignificance) {
		this.caseSignificance = caseSignificance;
	}
	

}
