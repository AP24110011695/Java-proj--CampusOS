package com.campusos.util;

/**
 * RFC-4180 style CSV field escaping.
 * - Fields containing commas, quotes or line breaks are wrapped in double quotes.
 * - Embedded quotes are doubled ("").
 * - A leading apostrophe protects against spreadsheet formula injection
 *   when the export is opened in Excel (=, +, -, @ prefixes).
 */
public final class CsvUtil {

    private CsvUtil() { }

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String field = String.valueOf(value);
        boolean needsQuoting = field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r");
        if (needsQuoting) {
            field = "\"" + field.replace("\"", "\"\"") + "\"";
        }
        // Guard against CSV formula injection in spreadsheet applications.
        if (field.matches("^[=+\\-@].*")) {
            field = "'" + field;
        }
        return field;
    }

    /** Joins fields into one CSV line using CRLF (RFC 4180 line separator). */
    public static String row(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
