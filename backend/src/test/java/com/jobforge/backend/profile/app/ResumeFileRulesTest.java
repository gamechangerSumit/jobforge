package com.jobforge.backend.profile.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ResumeFileRulesTest {

    @Test
    void acceptsPdfByMagicBytesOnly() {
        assertThat(SeekerAssetsService.sniff("%PDF-1.7 rest".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo(SeekerAssetsService.PDF);
        assertThat(SeekerAssetsService.sniff("<html>not a pdf</html>".getBytes(StandardCharsets.ISO_8859_1))).isNull();
    }

    @Test
    void acceptsDocxButRejectsPlainZip() {
        byte[] docx = new byte[] {'P', 'K', 3, 4};
        byte[] withParts = concat(docx, "[Content_Types].xml word/document.xml".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(SeekerAssetsService.sniff(withParts)).isEqualTo(SeekerAssetsService.DOCX);
        assertThat(SeekerAssetsService.sniff(concat(docx, "photo.jpg".getBytes(StandardCharsets.ISO_8859_1)))).isNull();
    }

    @Test
    void sanitizesFilenames() {
        assertThat(SeekerAssetsService.safeName("..\\evil/../cv<1>.pdf", SeekerAssetsService.PDF)).isEqualTo("cv1.pdf");
        assertThat(SeekerAssetsService.safeName(null, SeekerAssetsService.DOCX)).isEqualTo("resume.docx");
        assertThat(SeekerAssetsService.safeName("resume", SeekerAssetsService.PDF)).isEqualTo("resume.pdf");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
