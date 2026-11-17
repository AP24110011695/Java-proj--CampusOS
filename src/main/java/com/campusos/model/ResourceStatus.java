package com.campusos.model;

/** Operational status of a campus resource. */
public enum ResourceStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    MAINTENANCE("Under Maintenance");

    private final String label;

    ResourceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
