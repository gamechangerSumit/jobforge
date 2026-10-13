package com.jobforge.backend.report.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobforge.backend.shared.error.ValidationFailedException;
import org.junit.jupiter.api.Test;

/**
 * S3-01: the moderation reason must be 10-500 characters after trimming (API_CONTRACT 12.11). Pure unit test (no
 * Spring, no Docker). Written but NOT RUN in the authoring session.
 */
class AdminReportReasonTest {

    @Test
    void rejectsNineCharactersAndWhitespacePaddedShortValues() {
        assertThrows(ValidationFailedException.class, () -> AdminReportController.normalizeReason("123456789"));
        assertThrows(ValidationFailedException.class, () -> AdminReportController.normalizeReason("   123456789   "));
        assertThrows(ValidationFailedException.class, () -> AdminReportController.normalizeReason("a" + " ".repeat(30)));
        assertThrows(ValidationFailedException.class, () -> AdminReportController.normalizeReason(" ".repeat(30)));
        assertThrows(ValidationFailedException.class, () -> AdminReportController.normalizeReason(null));
    }

    @Test
    void acceptsTenAndFiveHundredCharactersAndReturnsTheTrimmedValue() {
        assertEquals("1234567890", AdminReportController.normalizeReason("1234567890"));
        assertEquals("1234567890", AdminReportController.normalizeReason("  1234567890\n"));
        String max = "x".repeat(500);
        assertEquals(max, AdminReportController.normalizeReason(max));
        assertEquals(max, AdminReportController.normalizeReason("   " + max + "   "));
    }

    @Test
    void rejectsFiveHundredAndOneCharacters() {
        assertThrows(ValidationFailedException.class, () -> AdminReportController.normalizeReason("x".repeat(501)));
        assertThrows(ValidationFailedException.class, () -> AdminReportController.normalizeReason("  " + "x".repeat(501)));
    }

    @Test
    void countsUnicodeCodePointsNotUtf16Units() {
        String tenEmoji = "\uD83D\uDE00".repeat(10);          // 10 code points, 20 UTF-16 units
        assertEquals(tenEmoji, AdminReportController.normalizeReason(tenEmoji));
        assertThrows(ValidationFailedException.class,
                () -> AdminReportController.normalizeReason("\uD83D\uDE00".repeat(9)));
    }

    @Test
    void trimsTheSameWhitespaceAsTheFrontend() {
        // NBSP, BOM and other Unicode spaces are trimmed (String.strip() would keep NBSP and BOM)
        assertThrows(ValidationFailedException.class,
                () -> AdminReportController.normalizeReason("a" + "\u00A0".repeat(30)));
        assertThrows(ValidationFailedException.class,
                () -> AdminReportController.normalizeReason("\uFEFF\u3000\u2003" + "123456789" + "\u202F\u2028"));
        assertEquals("1234567890", AdminReportController.normalizeReason("\u00A0\uFEFF 1234567890\u3000\n"));
        // inner whitespace is kept and counted
        assertEquals("ab cdefghij", AdminReportController.normalizeReason("  ab cdefghij  "));
    }
}
