package com.campusos.model;

/** State of one user's registration for one event. */
public enum RegistrationStatus {
    REGISTERED("Registered"),
    CANCELLED("Cancelled"),
    ATTENDED("Attended");

    private final String label;

    RegistrationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
