package com.enterprise.knowledge.support;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The single PostgreSQL container shared by every integration test class.
 *
 * It is started here and deliberately never stopped. That is the documented
 * Testcontainers singleton pattern, and it is used instead of the
 * {@code @Testcontainers} extension for a specific reason: that extension stops
 * a static container when its declaring class finishes, so the second test class
 * to run would inherit a stopped container and fail to connect to a port that no
 * longer exists.
 *
 * Ryuk, Testcontainers' own reaper, removes the container when the JVM exits.
 *
 * Loading this class starts a container, so nothing may touch it unless Docker
 * is available. {@link DockerAvailability} is consulted first, from a class that
 * does not reference this one.
 */
final class PostgresContainer {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>(
            // The pgvector image rather than plain PostgreSQL: these tests exist to
            // exercise vector search and its filter translation, which do not exist
            // without the extension.
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("knowledge_base_test")
            .withUsername("knowledge")
            .withPassword("test-password");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
