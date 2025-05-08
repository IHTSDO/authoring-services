package org.ihtsdo.authoringservices.domain;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;

public enum RMPTaskStatus {

    NEW("NEW"),
    ACCEPTED("ACCEPTED"),
    IN_PROGRESS("IN PROGRESS"),
    READY_FOR_RELEASE("READY FOR RELEASE"),
    PUBLISHED("PUBLISHED"),
    ON_HOLD("ON HOLD"),
    CLOSED("CLOSED"),
    CLARIFICATION_REQUESTED("CLARIFICATION REQUESTED"),
    APPEAL_CLARIFICATION_REQUESTED("APPEAL CLARIFICATION REQUESTED"),
    UNDER_APPEAL("UNDER APPEAL"),
    APPEAL_REJECTED("APPEAL REJECTED"),
    WITHDRAWN("WITHDRAWN"),
    REJECTED("REJECTED"),
    UNKNOWN("UNKNOWN");;

    private final String label;

    RMPTaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RMPTaskStatus fromLabel(String label) {
        for (RMPTaskStatus taskStatus : RMPTaskStatus.values()) {
            if (taskStatus.label.equals(label)) {
                return taskStatus;
            }
        }
        return RMPTaskStatus.UNKNOWN;
    }

    public static RMPTaskStatus fromLabelOrThrow(String label) throws BusinessServiceException {
        final RMPTaskStatus taskStatus = fromLabel(label);
        if (taskStatus == RMPTaskStatus.UNKNOWN) {
            throw new BusinessServiceException("Unrecognised RMP task status '" + label + "'.");
        }
        return taskStatus;
    }

}
