package com.enterprise.knowledge.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for tests that need a real database.
 *
 * These run against PostgreSQL with pgvector in a container, so the retrieval
 * filter is genuinely compiled into SQL and genuinely applied by the database.
 * Running them against an in-memory database would prove nothing about
 * production.
 *
 * The container is a never-stopped singleton shared by every subclass, so the
 * suite pays for one PostgreSQL startup rather than one per class. When no
 * Docker daemon is available the whole class is skipped rather than failed: a
 * skipped test reports honestly that it did not run, whereas a failure would
 * look like a defect in the application.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiConfiguration.class)
@ExtendWith(RequiresDocker.class)
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", PostgresContainer.INSTANCE::getPassword);
    }
}
