package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.demo.LocalEmbeddingModel;
import com.enterprise.knowledge.document.DocumentVisibility;
import com.enterprise.knowledge.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves that the authorization filter is honoured by the in-memory vector store
 * used by the demo profile.
 *
 * This matters for two reasons. First, a demo that quietly loses tenant
 * isolation would be worse than no demo at all. Second, it needs no Docker, so
 * the central claim of this project is verified on every {@code mvn test} run
 * rather than only when a container engine happens to be available.
 *
 * The companion {@code TenantIsolationIntegrationTest} proves the same property
 * against real PostgreSQL and pgvector, where the filter is compiled into SQL.
 */
class SimpleVectorStoreTenantIsolationTest {

    private static final UUID ACME = UUID.randomUUID();
    private static final UUID GLOBEX = UUID.randomUUID();

    private VectorStore vectorStore;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        vectorStore = SimpleVectorStore.builder(new LocalEmbeddingModel()).build();
        ragService = new RagService(vectorStore, mock(ChatClient.class), new AppProperties());

        // Deliberately near-identical documents in two organisations. Without a
        // working filter, a query would rank both highly and return them together.
        vectorStore.add(List.of(
                chunk(ACME, "acme-leave-policy.txt", DocumentVisibility.ALL_EMPLOYEES, 1,
                        "Acme Corporation parental leave policy. Acme employees receive "
                                + "twenty six weeks of paid parental leave processed by payroll."),
                chunk(ACME, "acme-executive-pay.txt", DocumentVisibility.ADMINS_ONLY, 2,
                        "Acme Corporation executive compensation. Acme director bonuses come "
                                + "from the confidential remuneration matrix approved by the board."),
                chunk(ACME, "acme-manager-guide.txt", DocumentVisibility.MANAGERS_AND_ADMINS, 3,
                        "Acme Corporation manager guide. Acme managers approve leave requests "
                                + "and conduct performance reviews each quarter."),
                chunk(GLOBEX, "globex-leave-policy.txt", DocumentVisibility.ALL_EMPLOYEES, 4,
                        "Globex Industries parental leave policy. Globex employees receive "
                                + "eight weeks of paid parental leave processed by payroll.")));
    }

    @Test
    @DisplayName("a search scoped to one tenant returns nothing from the other")
    void searchIsScopedToOneTenant() {
        List<Document> results = search("parental leave policy paid weeks payroll", ACME, Role.ADMIN);

        assertThat(results).isNotEmpty();
        assertThat(results)
                .allSatisfy(chunk -> assertThat(chunk.getMetadata().get(VectorMetadata.TENANT_ID))
                        .isEqualTo(ACME.toString()));
        assertThat(results).noneSatisfy(chunk -> assertThat(chunk.getText()).contains("Globex"));
    }

    @Test
    @DisplayName("the mirror case holds: Globex never sees Acme")
    void theMirrorCaseAlsoHolds() {
        List<Document> results = search("parental leave policy paid weeks payroll", GLOBEX, Role.ADMIN);

        assertThat(results).isNotEmpty();
        assertThat(results)
                .allSatisfy(chunk -> assertThat(chunk.getMetadata().get(VectorMetadata.TENANT_ID))
                        .isEqualTo(GLOBEX.toString()));
        assertThat(results).noneSatisfy(chunk -> assertThat(chunk.getText()).contains("Acme"));
    }

    @Test
    @DisplayName("an employee cannot retrieve an admins-only document")
    void employeeCannotRetrieveAdminOnlyDocument() {
        List<Document> results = search(
                "executive compensation director bonuses remuneration matrix", ACME, Role.EMPLOYEE);

        assertThat(results)
                .as("the restricted document must not be retrievable by an employee")
                .noneSatisfy(chunk -> assertThat(chunk.getMetadata().get(VectorMetadata.FILE_NAME))
                        .isEqualTo("acme-executive-pay.txt"));
    }

    @Test
    @DisplayName("an employee cannot retrieve a managers-and-admins document")
    void employeeCannotRetrieveManagerDocument() {
        List<Document> results = search(
                "manager guide approve leave requests performance reviews", ACME, Role.EMPLOYEE);

        assertThat(results)
                .noneSatisfy(chunk -> assertThat(chunk.getMetadata().get(VectorMetadata.FILE_NAME))
                        .isEqualTo("acme-manager-guide.txt"));
    }

    @Test
    @DisplayName("a manager reaches manager-level documents but not admins-only ones")
    void managerSeesManagerLevelButNotAdminOnly() {
        List<Document> managerDocuments = search(
                "manager guide approve leave requests performance reviews", ACME, Role.MANAGER);

        assertThat(managerDocuments)
                .as("a manager should reach the manager guide")
                .anySatisfy(chunk -> assertThat(chunk.getMetadata().get(VectorMetadata.FILE_NAME))
                        .isEqualTo("acme-manager-guide.txt"));

        List<Document> restricted = search(
                "executive compensation director bonuses remuneration matrix", ACME, Role.MANAGER);

        assertThat(restricted)
                .noneSatisfy(chunk -> assertThat(chunk.getMetadata().get(VectorMetadata.FILE_NAME))
                        .isEqualTo("acme-executive-pay.txt"));
    }

    @Test
    @DisplayName("an admin reaches every visibility level within their own tenant")
    void adminReachesEveryLevelInOwnTenant() {
        List<Document> results = search("Acme", ACME, Role.ADMIN);

        assertThat(results)
                .as("all three Acme documents should be reachable by an admin")
                .extracting(chunk -> chunk.getMetadata().get(VectorMetadata.FILE_NAME))
                .contains("acme-leave-policy.txt", "acme-executive-pay.txt", "acme-manager-guide.txt");
    }

    @Test
    @DisplayName("a tenant with no documents gets no results rather than someone else's")
    void unknownTenantGetsNothing() {
        List<Document> results = search("parental leave policy", UUID.randomUUID(), Role.ADMIN);

        assertThat(results).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private List<Document> search(String query, UUID tenantId, Role role) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(20)
                .similarityThreshold(0.0)
                .filterExpression(ragService.authorizationFilter(tenantId, role))
                .build());
    }

    private static Document chunk(UUID tenantId, String fileName, DocumentVisibility visibility,
                                  int pageNumber, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(VectorMetadata.TENANT_ID, tenantId.toString());
        metadata.put(VectorMetadata.DOCUMENT_ID, UUID.randomUUID().toString());
        metadata.put(VectorMetadata.FILE_NAME, fileName);
        metadata.put(VectorMetadata.VISIBILITY, visibility.name());
        metadata.put(VectorMetadata.PAGE_NUMBER, pageNumber);
        return new Document(text, metadata);
    }
}
