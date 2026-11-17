package com.campusos;

import com.campusos.util.CsvUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** RFC-4180 CSV escaping including comma, quote, newline and injection cases. */
class CsvUtilTest {

    @Test
    @DisplayName("plain values pass through unchanged")
    void plainValues() {
        assertEquals("hello", CsvUtil.escape("hello"));
        assertEquals("123", CsvUtil.escape(123));
        assertEquals("", CsvUtil.escape(null));
    }

    @Test
    @DisplayName("values containing commas are quoted")
    void commas() {
        assertEquals("\"Lab 1, Block A\"", CsvUtil.escape("Lab 1, Block A"));
    }

    @Test
    @DisplayName("embedded quotes are doubled and the field quoted")
    void quotes() {
        assertEquals("\"He said \"\"hello\"\"\"", CsvUtil.escape("He said \"hello\""));
    }

    @Test
    @DisplayName("line breaks force quoting")
    void newlines() {
        assertEquals("\"line1\nline2\"", CsvUtil.escape("line1\nline2"));
        assertEquals("\"line1\rline2\"", CsvUtil.escape("line1\rline2"));
    }

    @Test
    @DisplayName("formula injection is neutralized with a leading apostrophe")
    void formulaInjection() {
        assertEquals("'=SUM(A1:A2)", CsvUtil.escape("=SUM(A1:A2)"));
        assertEquals("'+cmd", CsvUtil.escape("+cmd"));
        assertEquals("'@import", CsvUtil.escape("@import"));
        assertEquals("'-1", CsvUtil.escape("-1"));
        // a plain negative-looking number inside text is still escaped
        assertTrue(CsvUtil.escape("-normal text").startsWith("'"));
    }

    @Test
    @DisplayName("row joins fields with commas and ends with CRLF")
    void rows() {
        String row = CsvUtil.row("a", "b,c", "d");
        assertEquals("a,\"b,c\",d\r\n", row);
    }

    @Test
    @DisplayName("round trip: messy record survives a naive CSV reader")
    void messyRecordRoundTrip() {
        String row = CsvUtil.row(1, "Sharma, Prof. \"Anita\"", "note with\nnewline");
        // naive reader: the quoted comma stays inside one field, the quoted
        // newline does not split the record - the field keeps its escaped form
        String[] lines = row.split("\r\n");
        assertEquals(1, lines.length, "quoted newline must not split the record");
        String[] fields = lines[0].split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        assertEquals(3, fields.length);
        assertEquals("\"Sharma, Prof. \"\"Anita\"\"\"", fields[1]);
    }
}
