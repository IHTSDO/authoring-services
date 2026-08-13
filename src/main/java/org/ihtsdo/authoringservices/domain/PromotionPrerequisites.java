package org.ihtsdo.authoringservices.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated promotion eligibility for a task.
 * Mirrors authoring-ui promotionService.js decision tree.
 */
public class PromotionPrerequisites {

	private String classificationStatus;
	private boolean classificationCurrent;
	private boolean equivalenciesFound;
	private String reviewStatus;
	private boolean sacSignedOff;
	private String branchState;
	private List<String> crsBlockingConcepts = new ArrayList<>();
	private List<String> blockers = new ArrayList<>();
	private boolean promotable;

	public String getClassificationStatus() {
		return classificationStatus;
	}

	public void setClassificationStatus(String classificationStatus) {
		this.classificationStatus = classificationStatus;
	}

	public boolean isClassificationCurrent() {
		return classificationCurrent;
	}

	public void setClassificationCurrent(boolean classificationCurrent) {
		this.classificationCurrent = classificationCurrent;
	}

	public boolean isEquivalenciesFound() {
		return equivalenciesFound;
	}

	public void setEquivalenciesFound(boolean equivalenciesFound) {
		this.equivalenciesFound = equivalenciesFound;
	}

	public String getReviewStatus() {
		return reviewStatus;
	}

	public void setReviewStatus(String reviewStatus) {
		this.reviewStatus = reviewStatus;
	}

	public boolean isSacSignedOff() {
		return sacSignedOff;
	}

	public void setSacSignedOff(boolean sacSignedOff) {
		this.sacSignedOff = sacSignedOff;
	}

	public String getBranchState() {
		return branchState;
	}

	public void setBranchState(String branchState) {
		this.branchState = branchState;
	}

	public List<String> getCrsBlockingConcepts() {
		return crsBlockingConcepts;
	}

	public void setCrsBlockingConcepts(List<String> crsBlockingConcepts) {
		this.crsBlockingConcepts = crsBlockingConcepts != null ? crsBlockingConcepts : new ArrayList<>();
	}

	public List<String> getBlockers() {
		return blockers;
	}

	public void setBlockers(List<String> blockers) {
		this.blockers = blockers != null ? blockers : new ArrayList<>();
	}

	public boolean isPromotable() {
		return promotable;
	}

	public void setPromotable(boolean promotable) {
		this.promotable = promotable;
	}
}
