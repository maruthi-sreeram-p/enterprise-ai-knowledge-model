package com.enterprise.knowledge.document.extraction;

/**
 * A unit of extracted text together with the page it came from.
 *
 * {@code pageNumber} is null for formats that genuinely have no pages. DOCX
 * pagination is decided by the renderer, not stored in the file, and a plain
 * text file has no pages at all. Reporting null there is what keeps citations
 * honest: the alternative would be to invent a number that points nowhere.
 *
 * @param pageNumber one-based page number, or null when the format has no pages
 * @param text       the text extracted from that page
 */
public record ExtractedPage(Integer pageNumber, String text) {
}
