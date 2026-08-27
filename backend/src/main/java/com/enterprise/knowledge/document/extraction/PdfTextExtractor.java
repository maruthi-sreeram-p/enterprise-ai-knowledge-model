package com.enterprise.knowledge.document.extraction;

import com.enterprise.knowledge.common.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts PDF text one page at a time.
 *
 * Reading page by page rather than in a single pass is the reason this
 * application can cite a real page number. PDFBox is used directly instead of a
 * generic extractor precisely because it exposes that page boundary.
 */
@Component
public class PdfTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    @Override
    public String extension() {
        return "pdf";
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public ExtractionResult extract(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {

            if (document.isEncrypted()) {
                throw new DocumentProcessingException(
                        "This PDF is password protected and cannot be indexed.");
            }

            int pageCount = document.getNumberOfPages();
            List<ExtractedPage> pages = new ArrayList<>(pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    pages.add(new ExtractedPage(pageNumber, text));
                }
            }

            if (pages.isEmpty()) {
                // Almost always a scanned document. This project does not run OCR,
                // and saying so is better than storing an empty index and letting
                // the user wonder why the file never answers anything.
                throw new DocumentProcessingException(
                        "No text could be extracted from this PDF. Scanned or image-only "
                                + "PDFs need optical character recognition, which this "
                                + "application does not perform.");
            }

            log.debug("Extracted {} non-empty pages of {} from {}", pages.size(), pageCount, file);
            return new ExtractionResult(pages, pageCount);

        } catch (IOException ex) {
            throw new DocumentProcessingException("The PDF file could not be read.", ex);
        }
    }
}
