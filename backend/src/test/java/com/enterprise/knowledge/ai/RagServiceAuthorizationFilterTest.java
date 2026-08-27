package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the shape of the retrieval filter without needing a database.
 *
 * The integration test proves the filter works end to end; this proves it is
 * built correctly for every role, which is cheap to check exhaustively and
 * catches a regression the moment it is introduced.
 */
class RagServiceAuthorizationFilterTest {

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(mock(VectorStore.class), mock(ChatClient.class), new AppProperties());
    }

    @Test
    @DisplayName("the filter always constrains the tenant")
    void filterAlwaysConstrainsTenant() {
        UUID tenantId = UUID.randomUUID();

        for (Role role : Role.values()) {
            Filter.Expression expression = ragService.authorizationFilter(tenantId, role);

            assertThat(expression.type())
                    .as("the top-level expression for %s must be an AND", role)
                    .isEqualTo(Filter.ExpressionType.AND);
            assertThat(expression.toString())
                    .as("the filter for %s must name the tenant", role)
                    .contains(tenantId.toString())
                    .contains("tenantId");
        }
    }

    @Test
    @DisplayName("an employee's filter admits only the open visibility level")
    void employeeFilterAdmitsOnlyOpenDocuments() {
        String filter = ragService.authorizationFilter(UUID.randomUUID(), Role.EMPLOYEE).toString();

        assertThat(filter).contains("ALL_EMPLOYEES");
        assertThat(filter)
                .as("an employee filter must not admit restricted levels")
                .doesNotContain("ADMINS_ONLY")
                .doesNotContain("MANAGERS_AND_ADMINS");
    }

    @Test
    @DisplayName("a manager's filter admits open and manager levels but not admin-only")
    void managerFilterExcludesAdminOnly() {
        String filter = ragService.authorizationFilter(UUID.randomUUID(), Role.MANAGER).toString();

        assertThat(filter).contains("ALL_EMPLOYEES").contains("MANAGERS_AND_ADMINS");
        assertThat(filter).doesNotContain("ADMINS_ONLY");
    }

    @Test
    @DisplayName("an admin's filter admits every visibility level")
    void adminFilterAdmitsEveryLevel() {
        String filter = ragService.authorizationFilter(UUID.randomUUID(), Role.ADMIN).toString();

        assertThat(filter)
                .contains("ALL_EMPLOYEES")
                .contains("MANAGERS_AND_ADMINS")
                .contains("ADMINS_ONLY");
    }

    @Test
    @DisplayName("two tenants never produce the same filter")
    void differentTenantsProduceDifferentFilters() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(ragService.authorizationFilter(first, Role.ADMIN).toString())
                .isNotEqualTo(ragService.authorizationFilter(second, Role.ADMIN).toString());
    }
}
