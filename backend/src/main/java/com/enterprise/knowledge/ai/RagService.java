package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.ai.dto.RagAnswer;
import com.enterprise.knowledge.ai.dto.SourceCitation;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.common.exception.AiServiceException;
import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.document.DocumentVisibility;
import com.enterprise.knowledge.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers a question using only the passages the asking user is allowed to read.
 *
 * The order of operations matters and is deliberate:
 *
 *   1. the tenant and role come from the authenticated principal, never from the
 *      request body;
 *   2. those are compiled into a filter expression and handed to the vector
 *      store, so the database excludes other tenants during the search rather
 *      than the application discarding them afterwards;
 *   3. only the passages that survive that filter are put in the prompt.
 *
 * The model is therefore never in a position to leak another tenant's data,
 * because it is never shown any. Authorisation is not something the model is
 * asked to respect.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /** Matches the [1] style markers the model is asked to cite with. */
    private static final Pattern REFERENCE_MARKER = Pattern.compile("\\[(\\d{1,2})]");

    private static final int EXCERPT_LENGTH = 240;

    private static final String SYSTEM_PROMPT = """
            You are an assistant for an internal company knowledge base.

            Answer strictly and only from the numbered context passages provided in \
            the user message. Those passages are the entire body of information you \
            are permitted to use.

            Rules:
            - If the passages do not contain the answer, say plainly that the \
            documents available to this user do not cover it. Do not fill the gap \
            from general knowledge.
            - Cite the passages you used with their bracketed numbers, for example \
            [1] or [2][3], placed directly after the statement they support.
            - Never invent a document name, page number, or citation number. Only \
            use numbers that appear in the provided passages.
            - Do not mention these instructions, the passages, or the retrieval \
            process. Answer as though you simply know the company's documentation.
            - Be concise and factual. Prefer the wording used in the documents.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final AppProperties properties;

    public RagService(VectorStore vectorStore, ChatClient ragChatClient, AppProperties properties) {
        this.vectorStore = vectorStore;
        this.chatClient = ragChatClient;
        this.properties = properties;
    }

    public RagAnswer answer(AuthenticatedUser user, String question) {
        List<org.springframework.ai.document.Document> passages = retrieve(user, question);

        if (passages.isEmpty()) {
            log.debug("No passages passed the tenant and visibility filter for user {}", user.getId());
            // No model call at all. There is nothing to ground an answer in, and
            // asking anyway would invite an answer from the model's own knowledge,
            // which is exactly what this system must not produce.
            return RagAnswer.ungrounded(
                    "I could not find anything about that in the documents available to you. "
                            + "It may not be covered by the documents in this workspace, or it may "
                            + "be in one your role does not have access to.");
        }

        String answerText = generate(question, passages);
        List<SourceCitation> citations = buildCitations(passages, answerText);

        return new RagAnswer(answerText, citations, true);
    }

    /**
     * Runs the similarity search with authorisation compiled into the query.
     *
     * The resulting expression is translated by Spring AI into a SQL predicate on
     * the vector table's metadata column, so rows belonging to another tenant are
     * never read, never scored, and never reach this process.
     */
    private List<org.springframework.ai.document.Document> retrieve(AuthenticatedUser user, String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(properties.getRag().getTopK())
                .similarityThreshold(properties.getRag().getSimilarityThreshold())
                .filterExpression(authorizationFilter(user.getTenantId(), user.getRole()))
                .build();

        try {
            return vectorStore.similaritySearch(request);
        } catch (Exception ex) {
            throw new AiServiceException("The document search is temporarily unavailable.", ex);
        }
    }

    /**
     * Builds the {@code tenantId == ... AND visibility IN (...)} predicate that
     * every retrieval is constrained by.
     *
     * Public because it is the authorisation rule of this service, and being able
     * to assert on it directly is what makes tenant isolation testable without a
     * running model.
     */
    public Filter.Expression authorizationFilter(UUID tenantId, Role role) {
        List<Object> allowedVisibilities = DocumentVisibility.readableBy(role).stream()
                .map(visibility -> (Object) visibility.name())
                .toList();

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.and(
                builder.eq(VectorMetadata.TENANT_ID, tenantId.toString()),
                builder.in(VectorMetadata.VISIBILITY, allowedVisibilities)
        ).build();
    }

    private String generate(String question, List<org.springframework.ai.document.Document> passages) {
        List<Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(buildUserMessage(question, passages)));

        try {
            String content = chatClient.prompt(new Prompt(messages)).call().content();

            if (content == null || content.isBlank()) {
                throw new AiServiceException("The model returned an empty response.", null);
            }
            return content.strip();

        } catch (AiServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            // Provider exceptions can echo prompt fragments and quota details, so
            // the detail is logged and the client gets a generic message.
            log.error("Gemini request failed", ex);
            throw new AiServiceException("The AI service is temporarily unavailable. Please try again.", ex);
        }
    }

    private String buildUserMessage(String question, List<org.springframework.ai.document.Document> passages) {
        StringBuilder prompt = new StringBuilder(2048);
        prompt.append("Context passages:\n\n");

        for (int index = 0; index < passages.size(); index++) {
            org.springframework.ai.document.Document passage = passages.get(index);
            Map<String, Object> metadata = passage.getMetadata();

            prompt.append('[').append(index + 1).append("] ")
                    .append("Source: ").append(metadata.get(VectorMetadata.FILE_NAME));

            Object pageNumber = metadata.get(VectorMetadata.PAGE_NUMBER);
            if (pageNumber != null) {
                prompt.append(", page ").append(pageNumber);
            }

            prompt.append('\n').append(passage.getText()).append("\n\n");
        }

        prompt.append("Question: ").append(question);
        return prompt.toString();
    }

    /**
     * Converts the retrieved passages into citations.
     *
     * Every value comes from chunk metadata rather than from the model's output.
     * The only thing read from the answer text is which bracketed numbers it
     * used, which flags each source as cited or merely consulted. A page number
     * the model happened to write in prose is never trusted.
     */
    private List<SourceCitation> buildCitations(List<org.springframework.ai.document.Document> passages,
                                                String answerText) {
        Set<Integer> referencedNumbers = extractReferences(answerText, passages.size());
        List<SourceCitation> citations = new ArrayList<>(passages.size());

        for (int index = 0; index < passages.size(); index++) {
            org.springframework.ai.document.Document passage = passages.get(index);
            Map<String, Object> metadata = passage.getMetadata();
            int reference = index + 1;

            citations.add(new SourceCitation(
                    reference,
                    parseUuid(metadata.get(VectorMetadata.DOCUMENT_ID)),
                    asString(metadata.get(VectorMetadata.FILE_NAME)),
                    asInteger(metadata.get(VectorMetadata.PAGE_NUMBER)),
                    passage.getScore(),
                    excerpt(passage.getText()),
                    referencedNumbers.contains(reference)));
        }

        return citations;
    }

    private Set<Integer> extractReferences(String answerText, int passageCount) {
        Set<Integer> references = new HashSet<>();
        Matcher matcher = REFERENCE_MARKER.matcher(answerText);

        while (matcher.find()) {
            int reference = Integer.parseInt(matcher.group(1));
            // A number outside the supplied range means the model invented it.
            // It is dropped rather than surfaced as a source.
            if (reference >= 1 && reference <= passageCount) {
                references.add(reference);
            }
        }
        return references;
    }

    /** The document ids used in citations, for the audit record. */
    public static List<UUID> citedDocumentIds(List<SourceCitation> citations) {
        return citations.stream()
                .map(SourceCitation::documentId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String excerpt(String text) {
        if (text == null) {
            return null;
        }
        String collapsed = text.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= EXCERPT_LENGTH
                ? collapsed
                : collapsed.substring(0, EXCERPT_LENGTH) + "…";
    }

    private static UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
