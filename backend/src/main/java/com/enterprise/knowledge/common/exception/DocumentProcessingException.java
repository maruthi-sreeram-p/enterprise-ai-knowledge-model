package com.enterprise.knowledge.common.exception;

import org.springframework.http.HttpStatus;

public class DocumentProcessingException extends ApiException {

    public DocumentProcessingException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_PROCESSING_FAILED", message);
    }

    public DocumentProcessingException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_PROCESSING_FAILED", message, cause);
    }
}
