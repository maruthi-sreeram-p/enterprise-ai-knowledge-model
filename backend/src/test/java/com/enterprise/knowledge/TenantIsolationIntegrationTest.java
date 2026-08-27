package com.enterprise.knowledge;

import com.enterprise.knowledge.ai.DocumentIngestionService;
import com.enterprise.knowledge.ai.RagService;
import com.enterprise.knowledge.ai.dto.RagAnswer;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.auth.JwtService;
import com.enterprise.knowledge.document.Document;
import com.enterprise.knowledge.document.DocumentRepository;
import com.enterprise.knowledge.document.DocumentStatus;
import com.enterprise.knowledge.document.DocumentVisibility;
import com.enterprise.knowledge.support.AbstractIntegrationTest;
import com.enterprise.knowledge.support.RecordingChatModel;
import com.enterprise.knowledge.tenant.Tenant;
import com.enterprise.knowledge.tenant.TenantRepository;
import com.enterprise.knowledge.user.Role;
import com.enterprise.knowledge.user.User;
import com.enterprise.knowledge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tests that matter most in this project.
 *
 * The claim being verified is that a user of one organisation cannot reach
 * another organisation's documents by any route: not through the REST API, not
 * through the vector search, and not indirectly by having that text placed into
 * a model prompt.
 *
 * These run against a real PostgreSQL with pgvector, so the filter expression is
 * genuinely translated into SQL and genuinely applied by the database. Only the
 * two Gemini calls are stubbed.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final String ACME_SECRET_TEXT = """
            Acme Corporation parental leave policy. Acme employees receive twenty six weeks \
            of paid parental leave. The Acme payroll department processes these claims within \
            fourteen days of submission.""";

    private static final String GLOBEX_SECRET_TEXT = """
            Globex Industries parental leave policy. Globex employees receive eight weeks of \
            paid parental leave. The Globex payroll department processes these claims within \
            thirty days of submission.""";

    private static final String ACME_RESTRICTED_TEXT = """
            Acme Corporation executive compensation schedule. Acme director bonuses are \
            calculated from the confidential remuneration matrix approved by the Acme board.""";

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentIngestionService ingestionService;
    @Autowired
    private RagService ragService;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ChatModel chatModel;

    @Value("${app.storage.root}")
    private String storageRoot;

    private Tenant acme;
    private Tenant globex;
    private User acmeAdmin;
    private User acmeEmployee;
    private User globexAdmin;
    private Document acmeDocument;
    private Document acmeRestrictedDocument;
    private Document globexDocument;

    @BeforeEach
    void setUpTwoOrganizations() throws IOException {
        // A unique slug and email suffix per run keeps repeated executions
        // independent without truncating tables between tests.
        String run = UUID.randomUUID().toString().substring(0, 8);

        acme = tenantRepository.save(new Tenant("Acme Corporation", "acme-" + run));
        globex = tenantRepository.save(new Tenant("Globex Industries", "globex-" + run));

        acmeAdmin = createUser(acme, "Acme Admin", "admin@acme-" + run + ".test", Role.ADMIN);
        acmeEmployee = createUser(acme, "Acme Employee", "employee@acme-" + run + ".test", Role.EMPLOYEE);
        globexAdmin = createUser(globex, "Globex Admin", "admin@globex-" + run + ".test", Role.ADMIN);

        acmeDocument = createAndIngest(acme, acmeAdmin, "acme-leave-policy.txt",
                ACME_SECRET_TEXT, DocumentVisibility.ALL_EMPLOYEES);
        acmeRestrictedDocument = createAndIngest(acme, acmeAdmin, "acme-executive-pay.txt",
                ACME_RESTRICTED_TEXT, DocumentVisibility.ADMINS_ONLY);
        globexDocument = createAndIngest(globex, globexAdmin, "globex-leave-policy.txt",
                GLOBEX_SECRET_TEXT, DocumentVisibility.ALL_EMPLOYEES);

        ((RecordingChatModel) chatModel).reset();
    }

    @Test
    @DisplayName("vector search for one tenant never returns another tenant's chunks")
    void vectorSearchIsScopedToTheCallersTenant() {
        // A question phrased to match both organisations' documents equally well.
        // Without the filter, the highest scoring chunks would come from both.
        String question = "parental leave policy paid weeks payroll department";

        List<org.springframework.ai.document.Document> acmeResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(20)
                        .similarityThreshold(0.0)
                        .filterExpression(ragService.authorizationFilter(acme.getId(), Role.ADMIN))
                        .build());

        assertThat(acmeResults).isNotEmpty();
        assertThat(acmeResults)
                .as("every chunk returned for Acme must be tagged with Acme's tenant id")
                .allSatisfy(chunk -> assertThat(chunk.getMetadata().get("tenantId"))
                        .isEqualTo(acme.getId().toString()));
        assertThat(acmeResults)
                .as("no Globex text may appear in Acme's results")
                .noneSatisfy(chunk -> assertThat(chunk.getText()).contains("Globex"));
    }

    @Test
    @DisplayName("the same query returns each tenant only its own answer material")
    void eachTenantSeesOnlyItsOwnCorpus() {
        String question = "parental leave policy paid weeks payroll department";

        List<org.springframework.ai.document.Document> globexResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(20)
                        .similarityThreshold(0.0)
                        .filterExpression(ragService.authorizationFilter(globex.getId(), Role.ADMIN))
                        .build());

        assertThat(globexResults).isNotEmpty();
        assertThat(globexResults)
                .allSatisfy(chunk -> assertThat(chunk.getMetadata().get("tenantId"))
                        .isEqualTo(globex.getId().toString()));
        assertThat(globexResults)
                .noneSatisfy(chunk -> assertThat(chunk.getText()).contains("Acme"));
    }

    @Test
    @DisplayName("another tenant's text is never placed into the model prompt")
    void otherTenantsTextNeverReachesThePrompt() {
        AuthenticatedUser acmeCaller = AuthenticatedUser.from(acmeAdmin);
        RecordingChatModel recorder = (RecordingChatModel) chatModel;

        RagAnswer answer = ragService.answer(acmeCaller, "How many weeks of parental leave do employees get?");

        assertThat(answer.grounded()).isTrue();
        assertThat(recorder.wasCalled()).isTrue();

        // The strongest available assertion: the other tenant's content was never
        // even shown to the model, so it had nothing to leak.
        assertThat(recorder.lastPrompt())
                .as("the prompt must not contain any Globex content")
                .doesNotContain("Globex")
                .doesNotContain("eight weeks")
                .contains("Acme");

        assertThat(answer.sources())
                .isNotEmpty()
                .allSatisfy(source -> assertThat(source.documentId())
                        .isIn(acmeDocument.getId(), acmeRestrictedDocument.getId()));
    }

    @Test
    @DisplayName("a role below a document's visibility does not retrieve its chunks")
    void restrictedDocumentsAreInvisibleToLowerRoles() {
        AuthenticatedUser employee = AuthenticatedUser.from(acmeEmployee);
        RecordingChatModel recorder = (RecordingChatModel) chatModel;

        RagAnswer answer = ragService.answer(employee, "How are director bonuses and executive compensation calculated?");

        // The employee is in the right tenant, so the restriction under test is the
        // visibility level rather than the tenant.
        if (recorder.wasCalled()) {
            assertThat(recorder.lastPrompt())
                    .as("an employee must not be shown an admins-only document")
                    .doesNotContain("remuneration matrix")
                    .doesNotContain("executive compensation schedule");
        }
        assertThat(answer.sources())
                .noneSatisfy(source -> assertThat(source.documentId())
                        .isEqualTo(acmeRestrictedDocument.getId()));
    }

    @Test
    @DisplayName("an admin reaching for another tenant's document by id gets 404")
    void fetchingAnotherTenantsDocumentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", globexDocument.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(acmeAdmin)))
                // 404 rather than 403, so the response cannot confirm that the
                // document exists somewhere else.
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the document list contains only the caller's own tenant")
    void documentListIsScopedToTheCallersTenant() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(acmeAdmin))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fileName")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.startsWith("acme-"))));
    }

    @Test
    @DisplayName("an employee does not see admins-only documents in the list")
    void documentListRespectsVisibility() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(acmeEmployee))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(
                                        acmeRestrictedDocument.getId().toString()))));
    }

    @Test
    @DisplayName("an employee is refused an admins-only document with 403")
    void employeeIsForbiddenFromRestrictedDocument() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", acmeRestrictedDocument.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(acmeEmployee)))
                // 403 rather than 404 here: within one organisation it is more
                // useful to learn that access is restricted than that nothing exists.
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a user cannot list another tenant's users")
    void userListIsScopedToTheCallersTenant() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(acmeAdmin))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].tenantId")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.equalTo(acme.getId().toString()))));
    }

    // ------------------------------------------------------------------ helpers

    private User createUser(Tenant tenant, String name, String email, Role role) {
        return userRepository.save(new User(
                tenant.getId(), name, email, passwordEncoder.encode("test-password-123"), role));
    }

    /**
     * Writes a text file, records the document, and runs the real ingestion
     * pipeline against it. Ingestion is invoked directly rather than through the
     * upload endpoint so the test does not have to wait on a background queue.
     */
    private Document createAndIngest(Tenant tenant, User uploader, String fileName,
                                     String content, DocumentVisibility visibility) throws IOException {
        UUID documentId = UUID.randomUUID();
        Path tenantDirectory = Paths.get(storageRoot).resolve(tenant.getId().toString());
        Files.createDirectories(tenantDirectory);

        Path file = tenantDirectory.resolve(documentId + ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        String relativePath = tenant.getId() + "/" + documentId + ".txt";
        Document document = documentRepository.save(new Document(
                tenant.getId(), fileName, relativePath, "text/plain",
                content.length(), uploader.getId(), visibility));

        ingestionService.ingest(document.getId(), tenant.getId());

        Document reloaded = documentRepository.findById(document.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("ingestion of %s must succeed for this test to mean anything", fileName)
                .isEqualTo(DocumentStatus.PROCESSED);

        return reloaded;
    }

    private String bearerFor(User user) {
        return "Bearer " + jwtService.issueToken(user).token();
    }
}
