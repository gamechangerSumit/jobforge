package com.jobforge.backend.report.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobforge.backend.shared.error.ValidationFailedException;
import org.junit.jupiter.api.Test;

/**
 * Report {@code details} rule (API_CONTRACT 12.9.1): at most 1000 UTF-16 code units on the RAW value, then trimmed with
 * the JavaScript {@code trim()} character set; empty after trimming means omitted. Pure unit test, written but NOT RUN.
 */
class ReportTextTest {

    private static final String EMOJI = "\uD83D\uDE00"; // 1 code point, 2 UTF-16 code units

    @Test
    void acceptsExactlyThousandCodeUnitsAndRejectsMore() {
        assertEquals("x".repeat(1000), ReportText.normalizeDetails("x".repeat(1000)));
        assertThrows(ValidationFailedException.class, () -> ReportText.normalizeDetails("x".repeat(1001)));
    }

    @Test
    void rawLengthIsCheckedBeforeTrimming() {
        // 1000 meaningful characters + 2 padding = 1002 raw code units: rejected although trimming gives 1000
        assertThrows(ValidationFailedException.class,
                () -> ReportText.normalizeDetails("x".repeat(1000) + "  "));
        assertThrows(ValidationFailedException.class,
                () -> ReportText.normalizeDetails("\u00A0" + "x".repeat(1000)));
        // padding inside the 1000 budget is fine and removed
        assertEquals("x".repeat(998), ReportText.normalizeDetails(" " + "x".repeat(998) + " "));
        assertThrows(ValidationFailedException.class, () -> ReportText.normalizeDetails(" ".repeat(1001)));
    }

    @Test
    void supplementaryCharactersCountAsTwoCodeUnits() {
        assertEquals(EMOJI.repeat(500), ReportText.normalizeDetails(EMOJI.repeat(500)));      // 1000 units
        assertThrows(ValidationFailedException.class, () -> ReportText.normalizeDetails(EMOJI.repeat(501))); // 1002
        assertEquals(EMOJI.repeat(499) + "xx", ReportText.normalizeDetails(EMOJI.repeat(499) + "xx")); // 1000
        assertThrows(ValidationFailedException.class,
                () -> ReportText.normalizeDetails(EMOJI.repeat(499) + "xxx")); // 1001
    }

    @Test
    void valuesBlankForJavaScriptTrimAreOmitted() {
        assertNull(ReportText.normalizeDetails(null));
        assertNull(ReportText.normalizeDetails(""));
        assertNull(ReportText.normalizeDetails("   \t\r\n "));
        assertNull(ReportText.normalizeDetails("\u00A0\u00A0\u00A0"));          // NBSP only
        assertNull(ReportText.normalizeDetails("\uFEFF"));                        // BOM only
        assertNull(ReportText.normalizeDetails("\u3000\u3000"));                 // ideographic space only
        assertNull(ReportText.normalizeDetails("\u2003\u202F\u205F\u2028\u2029\u1680")); // other Unicode spaces
    }

    @Test
    void leadingAndTrailingWhitespaceIsRemovedAndInnerWhitespaceKept() {
        assertEquals("asks for a fee", ReportText.normalizeDetails("  asks for a fee  "));
        assertEquals("hi", ReportText.normalizeDetails("\u00A0\uFEFF\u3000 hi \u00A0\uFEFF\n"));
        assertEquals("a \u00A0 b", ReportText.normalizeDetails(" a \u00A0 b "));
        assertEquals(EMOJI + "x", ReportText.normalizeDetails(" " + EMOJI + "x "));
    }

    @Test
    void trimMatchesTheResolveReasonRule() {
        assertEquals("1234567890", ReportText.trim("\u00A0 1234567890 \uFEFF"));
        assertEquals("", ReportText.trim("\u3000\u00A0"));
    }
}
