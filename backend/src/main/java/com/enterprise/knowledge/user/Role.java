package com.enterprise.knowledge.user;

/**
 * The three roles the application recognises, ordered from most to least
 * privileged. {@link #rank()} makes that ordering explicit so document
 * visibility rules can compare roles without a chain of if-statements.
 */
public enum Role {

    /** Manages users, uploads and deletes documents, reads audit logs. */
    ADMIN(3),

    /** Asks questions and reads documents up to manager visibility. */
    MANAGER(2),

    /** Asks questions and reads documents marked as visible to all employees. */
    EMPLOYEE(1);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean isAtLeast(Role other) {
        return this.rank >= other.rank;
    }

    /** Spring Security expects authority strings to carry the ROLE_ prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
