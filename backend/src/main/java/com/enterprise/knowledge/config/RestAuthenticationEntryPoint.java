package com.enterprise.knowledge.config;

import com.enterprise.knowledge.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns the standard {@link ApiError} body for unauthenticated requests.
 * Without this, Spring Security would answer with an empty 401 or an HTML login
 * page, neither of which a JSON client can interpret.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED",
                "Authentication is required to access this resource.", request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
