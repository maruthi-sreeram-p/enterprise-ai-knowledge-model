package com.enterprise.knowledge.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for errors this application raises deliberately and knows how to
 * translate into a clean HTTP response. Anything not extending this class is
 * treated as an unexpected failure and reported as a generic 500.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
