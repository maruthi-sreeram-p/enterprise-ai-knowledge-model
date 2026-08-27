package com.enterprise.knowledge.document.extraction;

import com.enterprise.knowledge.common.exception.DocumentProcessingException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Extracts text from a Word document.
 *
 * A DOCX file stores a flow of paragraphs, not pages; page breaks are decided
 * by whatever renders it. So the whole document is returned as a single unit
 * with a null page number, and citations for these files name the document
 * without claiming a page.
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public String extension() {
        return "docx";
    }

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    @Override
    public ExtractionResult extract(Path file) {
        try (InputStream input = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String text = extractor.getText();

            if (text == null || text.isBlank()) {
                throw new DocumentProcessingException(
                        "No text could be extracted from this Word document.");
            }

            return new ExtractionResult(List.of(new ExtractedPage(null, text)), null);

        } catch (IOException | RuntimeException ex) {
            if (ex instanceof DocumentProcessingException documentProcessingException) {
                throw documentProcessingException;
            }
            // POI throws a variety of unchecked exceptions for malformed input.
            // They are wrapped so the caller sees one predictable failure type.
            throw new DocumentProcessingException("The Word document could not be read.", ex);
        }
    }
}
