package com.campusos.model;

/** Lifecycle states of a complaint (SUBMITTED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED). */
public enum ComplaintStatus {
    SUBMITTED("Submitted"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    private final String label;

    ComplaintStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
