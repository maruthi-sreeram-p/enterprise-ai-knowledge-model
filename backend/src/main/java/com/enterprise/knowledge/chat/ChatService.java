package com.enterprise.knowledge.chat;

import com.enterprise.knowledge.ai.AnswerCache;
import com.enterprise.knowledge.ai.RagService;
import com.enterprise.knowledge.ai.dto.RagAnswer;
import com.enterprise.knowledge.ai.dto.SourceCitation;
import com.enterprise.knowledge.audit.AuditAction;
import com.enterprise.knowledge.audit.AuditService;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.chat.dto.ChatRequest;
import com.enterprise.knowledge.chat.dto.ChatResponse;
import com.enterprise.knowledge.chat.dto.ConversationDetailResponse;
import com.enterprise.knowledge.chat.dto.ConversationResponse;
import com.enterprise.knowledge.chat.dto.MessageResponse;
import com.enterprise.knowledge.common.exception.ResourceNotFoundException;
import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.ratelimit.RateLimiter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates a question: rate limit, resolve the thread, retrieve and answer,
 * persist both turns, audit.
 *
 * The method is not wrapped in a single transaction. Generating an answer
 * involves two network round trips (embedding the question, then generating
 * text) and can take several seconds; holding a database connection open across
 * that would exhaust the pool long before the model became the bottleneck.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final TypeReference<List<SourceCitation>> CITATION_LIST = new TypeReference<>() {
    };

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RagService ragService;
    private final AnswerCache answerCache;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       RagService ragService,
                       AnswerCache answerCache,
                       RateLimiter rateLimiter,
                       AuditService auditService,
                       ObjectMapper objectMapper,
                       AppProperties properties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.ragService = ragService;
        this.answerCache = answerCache;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ChatResponse ask(AuthenticatedUser actor, ChatRequest request) {
        rateLimiter.checkAiQuota(actor.getId());

        String question = request.question().strip();
        Conversation conversation = resolveConversation(actor, request.conversationId(), question);

        messageRepository.save(Message.userMessage(conversation.getId(), question));

        RagAnswer answer = answerCache.lookup(actor, question)
                .orElseGet(() -> generateAndCache(actor, question));

        Message assistantMessage = messageRepository.save(Message.assistantMessage(
                conversation.getId(), answer.answer(), serialiseSources(answer.sources())));

        conversation.touch();
        conversationRepository.save(conversation);

        auditAiQuery(actor, conversation, question, answer);

        return new ChatResponse(
                conversation.getId(),
                assistantMessage.getId(),
                conversation.getTitle(),
                answer.answer(),
                answer.sources(),
                answer.grounded(),
                assistantMessage.getCreatedAt());
    }

    private RagAnswer generateAndCache(AuthenticatedUser actor, String question) {
        RagAnswer answer = ragService.answer(actor, question);
        // Only grounded answers are cached. A "nothing found" reply is cheap to
        // reproduce and is likely to change as soon as a document is uploaded.
        if (answer.grounded()) {
            answerCache.store(actor, question, answer);
        }
        return answer;
    }

    /**
     * Finds the named conversation or starts a new one titled after the question.
     *
     * The lookup is scoped by tenant and by owning user, so naming someone else's
     * conversation id produces a 404 rather than appending to their thread.
     */
    private Conversation resolveConversation(AuthenticatedUser actor, UUID conversationId,
                                             String question) {
        if (conversationId == null) {
            return conversationRepository.save(new Conversation(
                    actor.getTenantId(), actor.getId(), Conversation.titleFrom(question)));
        }

        return conversationRepository
                .findByIdAndTenantIdAndUserId(conversationId, actor.getTenantId(), actor.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Conversation", conversationId));
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(AuthenticatedUser actor, Pageable pageable) {
        return conversationRepository
                .findByTenantIdAndUserIdOrderByUpdatedAtDesc(actor.getTenantId(), actor.getId(), pageable)
                .map(ConversationResponse::from);
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(AuthenticatedUser actor, UUID conversationId) {
        Conversation conversation = requireOwnConversation(actor, conversationId);

        List<MessageResponse> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessageResponse)
                .toList();

        return new ConversationDetailResponse(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(), messages);
    }

    @Transactional
    public void deleteConversation(AuthenticatedUser actor, UUID conversationId) {
        Conversation conversation = requireOwnConversation(actor, conversationId);

        // Messages are removed explicitly rather than relying only on the database
        // cascade, so the behaviour is the same whichever way the row is deleted.
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);

        log.info("User {} deleted conversation {}", actor.getId(), conversationId);
        auditService.record(actor, AuditAction.CONVERSATION_DELETED, conversationId, null);
    }

    private Conversation requireOwnConversation(AuthenticatedUser actor, UUID conversationId) {
        return conversationRepository
                .findByIdAndTenantIdAndUserId(conversationId, actor.getTenantId(), actor.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Conversation", conversationId));
    }

    /**
     * Records that a question was asked.
     *
     * By default the question and answer text are left out. Both routinely
     * contain confidential passages from company documents, and copying them into
     * a long-lived audit table duplicates that data somewhere with a different
     * retention policy and different access controls. What is always recorded is
     * enough to investigate misuse: who asked, when, in which tenant, how long
     * the question was, and which documents were cited.
     */
    private void auditAiQuery(AuthenticatedUser actor, Conversation conversation,
                              String question, RagAnswer answer) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("conversationId", conversation.getId().toString());
        metadata.put("questionLength", question.length());
        metadata.put("grounded", answer.grounded());
        metadata.put("sourceCount", answer.sources().size());
        metadata.put("citedDocumentIds", RagService.citedDocumentIds(answer.sources()).stream()
                .map(UUID::toString).toList());

        if (properties.getAudit().isStoreQueryText()) {
            metadata.put("question", question);
            metadata.put("answer", answer.answer());
        }

        auditService.record(actor, AuditAction.AI_QUERY, conversation.getId(), metadata);
    }

    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(),
                deserialiseSources(message.getSourcesJson()).orElse(null), message.getCreatedAt());
    }

    private String serialiseSources(List<SourceCitation> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception ex) {
            // The answer is still worth keeping even if its citations cannot be
            // written, so this degrades rather than failing the request.
            log.warn("Could not serialise source citations", ex);
            return null;
        }
    }

    private Optional<List<SourceCitation>> deserialiseSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(sourcesJson, CITATION_LIST));
        } catch (Exception ex) {
            log.warn("Could not read stored source citations", ex);
            return Optional.empty();
        }
    }
}
