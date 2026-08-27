package com.enterprise.knowledge.ai.dto;

import java.util.List;

/**
 * @param answer    the generated text
 * @param sources   the passages the answer was grounded in
 * @param grounded  false when nothing relevant was retrieved, in which case no
 *                  model call was made at all and the answer says so
 */
public record RagAnswer(String answer, List<SourceCitation> sources, boolean grounded) {

    public static RagAnswer ungrounded(String answer) {
        return new RagAnswer(answer, List.of(), false);
    }
}
