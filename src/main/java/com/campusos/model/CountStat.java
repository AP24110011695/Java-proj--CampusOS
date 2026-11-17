package com.campusos.model;

/** A label + count pair used by dashboard cards and charts (from GROUP BY queries). */
public class CountStat {

    private final String label;
    private final long value;

    public CountStat(String label, long value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() { return label; }
    public long getValue() { return value; }
}
