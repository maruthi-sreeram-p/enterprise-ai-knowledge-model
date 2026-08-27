package com.enterprise.knowledge.document.extraction;

import java.util.List;

/**
 * @param pages     extracted text, one entry per page for paginated formats and
 *                  a single entry for formats without pages
 * @param pageCount total pages, or null when the format has no concept of pages
 */
public record ExtractionResult(List<ExtractedPage> pages, Integer pageCount) {

    public boolean isEmpty() {
        return pages.stream().allMatch(page -> page.text() == null || page.text().isBlank());
    }
}
