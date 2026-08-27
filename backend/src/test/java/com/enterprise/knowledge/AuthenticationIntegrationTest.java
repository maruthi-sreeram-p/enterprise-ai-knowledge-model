package com.enterprise.knowledge;

import com.enterprise.knowledge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("registering an organization returns a usable token and an ADMIN user")
    void registrationCreatesAnAdminAndReturnsAToken() throws Exception {
        String email = uniqueEmail();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("Northwind Traders", "Ada Lovelace", email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.organizationName").value("Northwind Traders"))
                // The password hash must never appear in a response body.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        String token = readToken(result);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("the same email cannot register twice")
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("First Org", "First User", email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("Second Org", "Second User", email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("login succeeds with the right password and fails with the wrong one")
    void loginChecksThePassword() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("Login Test Org", "Grace Hopper", email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "correct-horse-battery"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "wrong-password"}""".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("an unknown email and a wrong password give the same response")
    void unknownEmailIsIndistinguishableFromWrongPassword() throws Exception {
        // Different responses here would let an attacker enumerate which addresses
        // hold accounts.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody-at-all@example.test", "password": "whatever-123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("protected endpoints reject a request with no token")
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage bearer token is rejected")
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("registration validates its input")
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName": "", "name": "", "email": "not-an-email", "password": "short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("an employee is forbidden from listing audit logs")
    void nonAdminCannotReadAuditLogs() throws Exception {
        String adminEmail = uniqueEmail();

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("RBAC Test Org", "Admin User", adminEmail)))
                .andExpect(status().isCreated())
                .andReturn();

        String adminToken = readToken(registration);
        String employeeEmail = uniqueEmail();

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Regular Employee", "email": "%s", \
                                "password": "employee-password-1", "role": "EMPLOYEE"}"""
                                .formatted(employeeEmail)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "employee-password-1"}"""
                                .formatted(employeeEmail)))
                .andExpect(status().isOk())
                .andReturn();

        String employeeToken = readToken(login);

        mockMvc.perform(get("/api/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // The admin who created them can read the trail.
        mockMvc.perform(get("/api/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an employee cannot create users")
    void employeeCannotCreateUsers() throws Exception {
        String adminEmail = uniqueEmail();

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("Create User Org", "Admin User", adminEmail)))
                .andExpect(status().isCreated())
                .andReturn();

        String adminToken = readToken(registration);
        String employeeEmail = uniqueEmail();

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Employee", "email": "%s", \
                                "password": "employee-password-1", "role": "EMPLOYEE"}"""
                                .formatted(employeeEmail)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "employee-password-1"}"""
                                .formatted(employeeEmail)))
                .andReturn();

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readToken(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sneaky Admin", "email": "%s", \
                                "password": "another-password-1", "role": "ADMIN"}"""
                                .formatted(uniqueEmail())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    private static String registrationBody(String organization, String name, String email) {
        return """
                {"organizationName": "%s", "name": "%s", "email": "%s", \
                "password": "correct-horse-battery"}""".formatted(organization, name, email);
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
