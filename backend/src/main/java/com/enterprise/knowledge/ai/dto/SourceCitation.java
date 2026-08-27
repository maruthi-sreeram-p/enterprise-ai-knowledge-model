package com.enterprise.knowledge.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * One source behind an answer.
 *
 * Every field is copied from the metadata of a chunk that was actually
 * retrieved. Nothing here is produced by the model, which is what stops the
 * application from reporting a page number that does not exist.
 *
 * @param reference  the marker used in the answer text, starting at 1
 * @param documentId identifier of the source document
 * @param fileName   the file the passage came from
 * @param pageNumber the page, or null for formats that have no pages
 * @param score      similarity score of the retrieved chunk, if the store reported one
 * @param excerpt    short snippet of the passage, so the user can judge relevance
 * @param cited      whether the answer text actually referenced this source
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceCitation(
        int reference,
        UUID documentId,
        String fileName,
        Integer pageNumber,
        Double score,
        String excerpt,
        boolean cited
) {
}
