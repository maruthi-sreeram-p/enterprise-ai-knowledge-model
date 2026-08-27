package com.enterprise.knowledge.document;

import com.enterprise.knowledge.common.exception.BadRequestException;
import com.enterprise.knowledge.document.extraction.DocumentTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileNameTest {

    @Test
    @DisplayName("an ordinary file name is kept as-is")
    void ordinaryNameIsKept() {
        assertThat(DocumentService.sanitizeFileName("Employee Handbook.pdf"))
                .isEqualTo("Employee Handbook.pdf");
    }

    @Test
    @DisplayName("directory components are stripped from a traversal attempt")
    void traversalAttemptIsReducedToBareName() {
        assertThat(DocumentService.sanitizeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.sanitizeFileName("..\\..\\windows\\system32\\config.txt"))
                .isEqualTo("config.txt");
        assertThat(DocumentService.sanitizeFileName("/absolute/path/report.pdf"))
                .isEqualTo("report.pdf");
        assertThat(DocumentService.sanitizeFileName("C:\\Users\\someone\\notes.txt"))
                .isEqualTo("notes.txt");
    }

    @Test
    @DisplayName("a name that reduces to nothing usable is rejected")
    void unusableNameIsRejected() {
        assertThatThrownBy(() -> DocumentService.sanitizeFileName("../"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DocumentService.sanitizeFileName(".."))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DocumentService.sanitizeFileName("   "))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DocumentService.sanitizeFileName(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("the extension is read from the last dot and lower-cased")
    void extensionIsDerivedFromTheLastDot() {
        assertThat(DocumentTextExtractor.extensionOf("Report.Final.PDF")).isEqualTo("pdf");
        assertThat(DocumentTextExtractor.extensionOf("notes.TXT")).isEqualTo("txt");
        assertThat(DocumentTextExtractor.extensionOf("archive.tar.gz")).isEqualTo("gz");
    }

    @Test
    @DisplayName("a name with no usable extension yields an empty one")
    void missingExtensionYieldsEmpty() {
        assertThat(DocumentTextExtractor.extensionOf("README")).isEmpty();
        assertThat(DocumentTextExtractor.extensionOf("trailing.")).isEmpty();
        assertThat(DocumentTextExtractor.extensionOf(null)).isEmpty();
    }
}
