package com.campusos.model;

/** Categories used to route complaints. */
public enum ComplaintCategory {
    INFRASTRUCTURE("Infrastructure"),
    ACADEMIC("Academic"),
    HOSTEL("Hostel"),
    IT_SUPPORT("IT Support"),
    TRANSPORT("Transport"),
    LIBRARY("Library"),
    OTHER("Other");

    private final String label;

    ComplaintCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
