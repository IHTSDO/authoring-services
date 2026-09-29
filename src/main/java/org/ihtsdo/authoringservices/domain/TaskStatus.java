package org.ihtsdo.authoringservices.domain;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;

public enum TaskStatus {

	NEW("New"),
	IN_PROGRESS("In Progress"),
	READY_FOR_REVIEW("Ready For Review"),
	IN_REVIEW("In Review"),
	REVIEW_COMPLETED("Review Completed"),
	PROMOTED("Promoted"),
	COMPLETED("Completed"),
	DELETED("Deleted"),
	AUTO_QUEUED("Auto Queued"),
	AUTO_REBASING("Auto Rebasing"),
	AUTO_CLASSIFYING("Auto Classifying"),
	AUTO_PROMOTING("Auto Promoting"),
	AUTO_CONFLICT("Auto Conflict"),
	UNKNOWN("Unknown");

	private final String label;

	TaskStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public boolean isReviewStatus() {
		return this == READY_FOR_REVIEW || this == IN_REVIEW || this == REVIEW_COMPLETED;
	}

	/**
	 * A task waiting for review is READY_FOR_REVIEW until a reviewer is assigned, then IN_REVIEW.
	 * Removing all reviewers from an IN_REVIEW task moves it back to READY_FOR_REVIEW.
	 */
	public static TaskStatus resolveReviewStatus(TaskStatus status, boolean hasReviewers) {
		if (status == IN_REVIEW && !hasReviewers) {
			return READY_FOR_REVIEW;
		}
		if (status == READY_FOR_REVIEW && hasReviewers) {
			return IN_REVIEW;
		}
		return status;
	}

	public static TaskStatus fromLabel(String label) {
		for (TaskStatus taskStatus : TaskStatus.values()) {
			if (taskStatus.label.equals(label)) {
				return taskStatus;
			}
		}
		return TaskStatus.UNKNOWN;
	}

	public static TaskStatus fromLabelOrThrow(String label) throws BusinessServiceException {
		final TaskStatus taskStatus = fromLabel(label);
		if (taskStatus == TaskStatus.UNKNOWN) {
			throw new BusinessServiceException("Unrecognised task status '" + label + "'.");
		}
		return taskStatus;
	}

}
