package org.ihtsdo.authoringservices.domain;

public enum ValidationJobStatus {
	NOT_TRIGGERED, STALE , SCHEDULED, COMPLETED, FAILED;

	private static ValidationJobStatus[] ALLOWED_STATES = new ValidationJobStatus[] { NOT_TRIGGERED, STALE, COMPLETED, FAILED };

	public static boolean isAllowedTriggeringState(String status) {
		for (ValidationJobStatus thisStatus : ALLOWED_STATES) {
			if (thisStatus.name().equals(status)) {
				return true;
			}
		}
		return false;
	}
}
