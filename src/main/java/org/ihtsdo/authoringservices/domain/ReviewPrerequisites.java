package org.ihtsdo.authoringservices.domain;

import java.util.ArrayList;
import java.util.List;

public class ReviewPrerequisites {

	public record UnsavedConcept(String conceptId, String fsn) {
	}

	private List<UnsavedConcept> unsavedConcepts = new ArrayList<>();
	private boolean classificationCurrent;
	private String classificationStatus;
	private boolean hasUncommittedChanges;
	private List<String> crsBlockingConcepts = new ArrayList<>();
	private List<String> blockers = new ArrayList<>();
	private boolean readyForReview;

	public List<UnsavedConcept> getUnsavedConcepts() {
		return unsavedConcepts;
	}

	public void setUnsavedConcepts(List<UnsavedConcept> unsavedConcepts) {
		this.unsavedConcepts = unsavedConcepts;
	}

	public boolean isClassificationCurrent() {
		return classificationCurrent;
	}

	public void setClassificationCurrent(boolean classificationCurrent) {
		this.classificationCurrent = classificationCurrent;
	}

	public String getClassificationStatus() {
		return classificationStatus;
	}

	public void setClassificationStatus(String classificationStatus) {
		this.classificationStatus = classificationStatus;
	}

	public boolean getHasUncommittedChanges() {
		return hasUncommittedChanges;
	}

	public void setHasUncommittedChanges(boolean hasUncommittedChanges) {
		this.hasUncommittedChanges = hasUncommittedChanges;
	}

	public List<String> getCrsBlockingConcepts() {
		return crsBlockingConcepts;
	}

	public void setCrsBlockingConcepts(List<String> crsBlockingConcepts) {
		this.crsBlockingConcepts = crsBlockingConcepts;
	}

	public List<String> getBlockers() {
		return blockers;
	}

	public void setBlockers(List<String> blockers) {
		this.blockers = blockers;
	}

	public boolean isReadyForReview() {
		return readyForReview;
	}

	public void setReadyForReview(boolean readyForReview) {
		this.readyForReview = readyForReview;
	}
}
