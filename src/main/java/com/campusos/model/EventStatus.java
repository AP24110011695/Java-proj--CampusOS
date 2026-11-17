package com.campusos.model;

/** Lifecycle states of a campus event. */
public enum EventStatus {
    UPCOMING("Upcoming"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    EventStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
