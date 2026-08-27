package com.enterprise.knowledge.document.extraction;

import java.nio.file.Path;

/**
 * Reads the text out of one document format.
 *
 * Implementations are Spring beans; {@link DocumentTextExtractor} picks the
 * right one. Adding a format means adding a bean, with nothing else to change.
 */
public interface TextExtractor {

    /** The file extension this extractor handles, lower case and without a dot. */
    String extension();

    /** The content type recorded for files of this format. */
    String contentType();

    /**
     * @throws com.enterprise.knowledge.common.exception.DocumentProcessingException
     *         when the file cannot be read as this format
     */
    ExtractionResult extract(Path file);
}
