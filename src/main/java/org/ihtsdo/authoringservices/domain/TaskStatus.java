package org.ihtsdo.authoringservices.domain;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;

public enum TaskStatus {

	NEW("New"),
	IN_PROGRESS("In Progress"),
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
