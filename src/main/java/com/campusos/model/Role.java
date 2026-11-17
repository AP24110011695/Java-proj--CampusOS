package com.campusos.model;

/** The three user roles supported by CampusOS. */
public enum Role {
    ADMIN("Administrator"),
    FACULTY("Faculty"),
    STUDENT("Student");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
