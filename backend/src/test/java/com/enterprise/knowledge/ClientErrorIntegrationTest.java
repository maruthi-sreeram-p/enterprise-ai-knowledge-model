package com.enterprise.knowledge;

import com.enterprise.knowledge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mistakes made by a client must come back as 4xx responses in the ApiError
 * shape. They used to fall into the catch-all handler and surface as 500s,
 * which look like server faults and hide the real problem from the caller.
 */
class ClientErrorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("the wrong HTTP method is 405, not 500")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("an unsupported content type is 415, not 500")
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("uploading without a file part is a 400, not a 500")
    void uploadWithoutAFileIsABadRequest() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(multipart("/api/documents/upload")
                        .param("visibility", "ALL_EMPLOYEES")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("uploading a non-multipart body is a 400, not a 500")
    void nonMultipartUploadIsABadRequest() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/documents/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    private String registerAndGetToken() throws Exception {
        String body = """
                {"organizationName": "Client Error Org", "name": "Admin User",
                 "email": "%s", "password": "correct-horse-battery"}"""
                .formatted("errors-" + UUID.randomUUID() + "@example.test");

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("accessToken").asText();
    }
}
