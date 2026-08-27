package com.enterprise.knowledge.document.extraction;

import com.enterprise.knowledge.common.exception.DocumentProcessingException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads a plain text file. Like DOCX, it has no pages, so the page number is
 * null.
 */
@Component
public class PlainTextExtractor implements TextExtractor {

    @Override
    public String extension() {
        return "txt";
    }

    @Override
    public String contentType() {
        return "text/plain";
    }

    @Override
    public ExtractionResult extract(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);

            if (text.isBlank()) {
                throw new DocumentProcessingException("This text file is empty.");
            }

            return new ExtractionResult(List.of(new ExtractedPage(null, text)), null);

        } catch (MalformedInputException ex) {
            throw new DocumentProcessingException(
                    "This text file is not valid UTF-8. Re-save it as UTF-8 and upload it again.", ex);
        } catch (IOException ex) {
            throw new DocumentProcessingException("The text file could not be read.", ex);
        }
    }
}
