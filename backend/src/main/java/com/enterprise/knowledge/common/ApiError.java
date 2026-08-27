package com.enterprise.knowledge.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error shape returned by every failing endpoint.
 *
 * @param timestamp   when the failure was rendered
 * @param status      HTTP status code
 * @param code        stable machine-readable error code
 * @param message     human-readable summary, safe to show to an end user
 * @param path        request path that failed
 * @param fieldErrors per-field validation messages, present only for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, List<String>> fieldErrors
) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }

    public static ApiError validation(int status, String message, String path,
                                      Map<String, List<String>> fieldErrors) {
        return new ApiError(Instant.now(), status, "VALIDATION_FAILED", message, path, fieldErrors);
    }
}
