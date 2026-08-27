package com.enterprise.knowledge.document;

import com.enterprise.knowledge.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The visibility rules are small enough to state exhaustively, and they decide
 * what goes into the retrieval filter, so they are worth pinning down precisely.
 */
class DocumentVisibilityTest {

    @Test
    @DisplayName("an employee can read only documents open to all employees")
    void employeeReadsOnlyOpenDocuments() {
        assertThat(DocumentVisibility.readableBy(Role.EMPLOYEE))
                .containsExactly(DocumentVisibility.ALL_EMPLOYEES);
    }

    @Test
    @DisplayName("a manager can read open and manager-level documents")
    void managerReadsOpenAndManagerDocuments() {
        assertThat(DocumentVisibility.readableBy(Role.MANAGER))
                .containsExactlyInAnyOrder(
                        DocumentVisibility.ALL_EMPLOYEES,
                        DocumentVisibility.MANAGERS_AND_ADMINS);
    }

    @Test
    @DisplayName("an admin can read every visibility level")
    void adminReadsEverything() {
        assertThat(DocumentVisibility.readableBy(Role.ADMIN))
                .containsExactlyInAnyOrder(DocumentVisibility.values());
    }

    @Test
    @DisplayName("a manager cannot read an admins-only document")
    void managerCannotReadAdminOnlyDocument() {
        assertThat(DocumentVisibility.ADMINS_ONLY.isReadableBy(Role.MANAGER)).isFalse();
        assertThat(DocumentVisibility.ADMINS_ONLY.isReadableBy(Role.EMPLOYEE)).isFalse();
        assertThat(DocumentVisibility.ADMINS_ONLY.isReadableBy(Role.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("role ranking is strictly ordered")
    void rolesAreOrdered() {
        assertThat(Role.ADMIN.isAtLeast(Role.MANAGER)).isTrue();
        assertThat(Role.MANAGER.isAtLeast(Role.EMPLOYEE)).isTrue();
        assertThat(Role.EMPLOYEE.isAtLeast(Role.MANAGER)).isFalse();
        assertThat(Role.MANAGER.isAtLeast(Role.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("every role can read at least one level, so the filter is never empty")
    void everyRoleHasSomethingReadable() {
        // An empty list would compile to an IN () clause, which is a SQL error in
        // PostgreSQL rather than a query that matches nothing.
        for (Role role : Role.values()) {
            assertThat(DocumentVisibility.readableBy(role)).isNotEmpty();
        }
    }
}
