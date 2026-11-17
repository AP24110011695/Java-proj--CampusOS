package com.campusos.model;

/** Types of bookable campus resources. */
public enum ResourceType {
    CLASSROOM("Classroom"),
    COMPUTER_LAB("Computer Lab"),
    SEMINAR_HALL("Seminar Hall"),
    PROJECTOR("Projector"),
    EQUIPMENT("Equipment"),
    OTHER("Other");

    private final String label;

    ResourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
