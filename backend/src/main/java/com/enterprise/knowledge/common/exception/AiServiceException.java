package com.enterprise.knowledge.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when the upstream model provider or the vector store fails. The
 * message returned to the client is deliberately generic: provider errors can
 * echo back prompt fragments, quota details, or key metadata.
 */
public class AiServiceException extends ApiException {

    public AiServiceException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message, cause);
    }
}
