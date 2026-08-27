package com.enterprise.knowledge.demo;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An offline stand-in for Gemini, used by the demo profile.
 *
 * It generates nothing. It reads the numbered passages that
 * {@code RagService} placed in the prompt, scores each sentence by how many of
 * the question's words it contains, and returns the best few sentences verbatim
 * with their citation markers.
 *
 * That makes it a genuinely honest demo of the surrounding machinery — retrieval,
 * tenant filtering, citation building, conversation storage, auditing all run for
 * real — while being completely transparent about the fact that no language model
 * was involved. Every answer says so in its last line, so a reader can never
 * mistake an extract for a generated answer.
 */
public class ExtractiveChatModel implements ChatModel {

    static final String DEMO_NOTICE =
            "_Demo mode: this reply was extracted verbatim from the retrieved passages. "
                    + "No language model was called. Configure GEMINI_API_KEY and drop the "
                    + "`demo` profile for real generated answers._";

    /** Matches the "[1] Source: file.pdf, page 3" headers RagService writes. */
    private static final Pattern PASSAGE_HEADER =
            Pattern.compile("\\[(\\d+)] Source: ([^\\n]*)\\n");

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    /** Words too common to say anything about relevance. */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "is", "are", "was", "were",
            "for", "on", "at", "by", "with", "do", "does", "did", "how", "what", "when",
            "where", "who", "why", "which", "can", "i", "we", "you", "they", "it", "this",
            "that", "there", "have", "has", "had", "be", "been", "as", "from", "our", "my");

    private static final int MAX_SENTENCES = 3;

    @Override
    public ChatResponse call(Prompt prompt) {
        String userText = prompt.getInstructions().stream()
                .filter(message -> message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER)
                .map(Message::getText)
                .collect(Collectors.joining("\n"));

        String answer = buildAnswer(userText);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    static String buildAnswer(String userText) {
        String question = extractQuestion(userText);
        List<Passage> passages = extractPassages(userText);

        if (passages.isEmpty()) {
            return "I could not read any usable passage from the documents available to you.\n\n"
                    + DEMO_NOTICE;
        }

        Set<String> questionWords = significantWords(question);
        List<ScoredSentence> scored = new ArrayList<>();

        for (Passage passage : passages) {
            for (String sentence : SENTENCE_BOUNDARY.split(passage.body())) {
                String cleaned = sentence.strip().replaceAll("\\s+", " ");
                if (cleaned.length() < 20) {
                    continue;
                }
                long overlap = significantWords(cleaned).stream()
                        .filter(questionWords::contains)
                        .count();
                scored.add(new ScoredSentence(cleaned, passage.reference(), overlap));
            }
        }

        List<ScoredSentence> best = scored.stream()
                .filter(sentence -> sentence.overlap() > 0)
                .sorted((left, right) -> Long.compare(right.overlap(), left.overlap()))
                .limit(MAX_SENTENCES)
                .toList();

        if (best.isEmpty()) {
            // Nothing in the retrieved text matches the question's wording. Saying so
            // is more useful than returning the first sentence and implying it is an
            // answer. A real model would often still find the answer here, because it
            // understands meaning rather than word overlap.
            return "The documents available to you were searched, but no passage in them "
                    + "matches the wording of that question closely enough for this offline "
                    + "responder to quote.\n\n" + DEMO_NOTICE;
        }

        StringBuilder answer = new StringBuilder();
        Set<Integer> citedReferences = new LinkedHashSet<>();

        for (ScoredSentence sentence : best) {
            answer.append(sentence.text());
            if (citedReferences.add(sentence.reference())) {
                answer.append(" [").append(sentence.reference()).append(']');
            }
            answer.append(' ');
        }

        return answer.toString().strip() + "\n\n" + DEMO_NOTICE;
    }

    private static String extractQuestion(String userText) {
        int marker = userText.lastIndexOf("Question:");
        return marker < 0 ? userText : userText.substring(marker + "Question:".length()).strip();
    }

    /** Splits the prompt's context block back into its numbered passages. */
    private static List<Passage> extractPassages(String userText) {
        List<Passage> passages = new ArrayList<>();
        Matcher matcher = PASSAGE_HEADER.matcher(userText);

        int previousReference = -1;
        int previousBodyStart = -1;

        while (matcher.find()) {
            if (previousReference > 0) {
                passages.add(new Passage(previousReference,
                        userText.substring(previousBodyStart, matcher.start()).strip()));
            }
            previousReference = Integer.parseInt(matcher.group(1));
            previousBodyStart = matcher.end();
        }

        if (previousReference > 0) {
            int end = userText.lastIndexOf("Question:");
            if (end < previousBodyStart) {
                end = userText.length();
            }
            passages.add(new Passage(previousReference,
                    userText.substring(previousBodyStart, end).strip()));
        }
        return passages;
    }

    private static Set<String> significantWords(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private record Passage(int reference, String body) {
    }

    private record ScoredSentence(String text, int reference, long overlap) {
    }
}
