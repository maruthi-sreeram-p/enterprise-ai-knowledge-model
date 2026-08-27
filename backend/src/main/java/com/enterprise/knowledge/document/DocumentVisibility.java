package com.enterprise.knowledge.document;

import com.enterprise.knowledge.user.Role;

import java.util.Arrays;
import java.util.List;

/**
 * How widely a document may be read inside its own tenant.
 *
 * This is a deliberately small model: three levels, each mapped to the minimum
 * role that may read it. It exists so that "retrieve only authorised chunks" is
 * a real constraint pushed into the vector query rather than a claim, without
 * growing into a general-purpose ACL system.
 */
public enum DocumentVisibility {

    ALL_EMPLOYEES(Role.EMPLOYEE),
    MANAGERS_AND_ADMINS(Role.MANAGER),
    ADMINS_ONLY(Role.ADMIN);

    private final Role minimumRole;

    DocumentVisibility(Role minimumRole) {
        this.minimumRole = minimumRole;
    }

    public Role getMinimumRole() {
        return minimumRole;
    }

    public boolean isReadableBy(Role role) {
        return role.isAtLeast(minimumRole);
    }

    /**
     * The visibility levels the given role may read. This list is what gets
     * compiled into the vector store filter expression, so that documents above
     * the caller's role are excluded by the database rather than filtered out
     * after retrieval.
     */
    public static List<DocumentVisibility> readableBy(Role role) {
        return Arrays.stream(values())
                .filter(visibility -> visibility.isReadableBy(role))
                .toList();
    }
}
