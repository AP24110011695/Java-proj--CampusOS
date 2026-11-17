package com.campusos.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Shared human-friendly date/time formatting. */
public final class Fmt {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Fmt() { }

    public static String date(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    public static String datetime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATETIME);
    }

    public static String time(LocalTime value) {
        return value == null ? "-" : value.format(TIME);
    }
}
