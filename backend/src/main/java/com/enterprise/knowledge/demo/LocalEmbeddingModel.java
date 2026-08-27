package com.enterprise.knowledge.demo;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An offline stand-in for the Gemini embedding model, used by the demo profile.
 *
 * It hashes each word into one of {@value #DIMENSIONS} buckets and counts
 * occurrences, then normalises the vector. Two texts that share vocabulary end
 * up with a high cosine similarity; two that share none end up near zero.
 *
 * This is emphatically <strong>not</strong> a semantic model. It cannot tell
 * that "holiday" and "annual leave" mean the same thing, which a real embedding
 * model can. It exists so the application can be run, clicked through, and
 * tested end to end without an API key or a network call. Anything that depends
 * on genuine semantic similarity needs the real model.
 */
public class LocalEmbeddingModel implements EmbeddingModel {

    /** Matches the pgvector column width used in the main configuration. */
    public static final int DIMENSIONS = 768;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>(request.getInstructions().size());

        for (int index = 0; index < request.getInstructions().size(); index++) {
            embeddings.add(new Embedding(embedText(request.getInstructions().get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public static float[] embedText(String text) {
        float[] vector = new float[DIMENSIONS];

        if (text != null) {
            for (String token : text.toLowerCase(Locale.ROOT).split("\\W+")) {
                if (!token.isEmpty()) {
                    vector[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1.0f;
                }
            }
        }

        double magnitude = 0.0;
        for (float value : vector) {
            magnitude += value * value;
        }

        // Cosine similarity is undefined for a zero vector, so empty text gets one
        // arbitrary non-zero component rather than producing NaN downstream.
        if (magnitude == 0.0) {
            vector[0] = 1.0f;
            return vector;
        }

        float norm = (float) Math.sqrt(magnitude);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
        return vector;
    }
}
