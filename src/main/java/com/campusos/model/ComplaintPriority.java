package com.campusos.model;

/** Urgency levels of a complaint. */
public enum ComplaintPriority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical");

    private final String label;

    ComplaintPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
