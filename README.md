# Enterprise AI Knowledge Base

A multi-tenant knowledge base where an organization uploads its internal
documents and its people ask questions about them in plain language. Answers are
generated from the organization's own documents and come back with the passages
they were drawn from.

Built as a single Spring Boot application. The AI runs inside it through Spring
AI — there is no separate Python service, no separate AI microservice, and no
orchestration framework.

---

## Table of contents

- [The problem](#the-problem)
- [Features](#features)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [How retrieval-augmented answering works here](#how-retrieval-augmented-answering-works-here)
- [Multi-tenancy](#multi-tenancy)
- [Security](#security)
- [Database design](#database-design)
- [API endpoints](#api-endpoints)
- [Running it without Docker or an API key](#running-it-without-docker-or-an-api-key)
- [Local setup](#local-setup)
- [Environment variables](#environment-variables)
- [Running with Docker](#running-with-docker)
- [Testing](#testing)
- [What is and is not implemented](#what-is-and-is-not-implemented)
- [Future improvements](#future-improvements)

---

## The problem

Company knowledge is scattered across handbooks, policies, and process
documents. Finding one fact means knowing which file holds it and where. Full-
text search only helps when you already know the words the document uses.

Handing those documents to a general chatbot creates two new problems:

1. **It makes things up.** A model asked about a policy it has never seen will
   answer anyway, confidently and wrongly.
2. **It has no idea who is asking.** An HR salary band and an engineering
   onboarding guide should not be equally readable by everyone, and one
   company's documents must never surface in another's answers.

This project addresses both. Answers are grounded in retrieved passages and cite
them, and the retrieval step is constrained by the asking user's organization
and role before the model sees anything.

---

## Features

| Area | What works |
| --- | --- |
| **Accounts** | Register an organization with its first administrator; sign in; JWT sessions |
| **Roles** | `ADMIN`, `MANAGER`, `EMPLOYEE`, enforced by Spring Security on every endpoint |
| **Users** | Administrators add, update, and remove people in their own organization |
| **Documents** | Upload PDF, DOCX, TXT; text extraction; chunking; embedding; delete |
| **Visibility** | Each document is readable by all employees, managers and above, or admins only |
| **Questions** | Natural-language questions answered from the caller's authorized documents |
| **Citations** | Every answer lists the passages behind it, with real page numbers where the format has pages |
| **Conversations** | Threads with full message history, private to the person who started them |
| **Audit log** | Logins, uploads, document access, deletions, and AI questions |
| **Rate limiting** | Per-user limit on AI questions, enforced in Redis across instances |
| **Async ingestion** | In-process by default; Kafka when configured |

---

## Architecture

```
┌──────────────────┐
│  Next.js client  │  client-rendered SPA, bearer token in the Authorization header
└────────┬─────────┘
         │ HTTPS / JSON
┌────────▼──────────────────────────────────────────────────────┐
│                  Spring Boot application                      │
│                                                               │
│  SecurityFilterChain → JwtAuthenticationFilter                │
│      establishes tenant + role from the signed token          │
│                                                               │
│  auth · user · tenant · document · chat · ai · audit          │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  ai/                                                    │  │
│  │    RagService              retrieve → ground → generate  │  │
│  │    DocumentIngestionService  extract → chunk → embed     │  │
│  │    AnswerCache             tenant- and role-scoped       │  │
│  └─────────────────────────────────────────────────────────┘  │
└───┬───────────────┬──────────────┬────────────────┬───────────┘
    │               │              │                │
┌───▼────────┐ ┌────▼─────┐ ┌──────▼──────┐ ┌───────▼────────┐
│ PostgreSQL │ │  Redis   │ │    Kafka    │ │  Gemini API    │
│ + pgvector │ │ limits + │ │  ingestion  │ │ chat +         │
│ data +     │ │ cache    │ │  events     │ │ embeddings     │
│ vectors    │ │          │ │  (optional) │ │ (external)     │
└────────────┘ └──────────┘ └─────────────┘ └────────────────┘
```

The backend is organized by domain rather than by layer:

```
com.enterprise.knowledge
├── auth        JWT issuing and verification, login, registration
├── user        User administration within one organization
├── tenant      The organization entity
├── document    Upload, storage, text extraction, visibility
├── chat        Conversations and messages
├── ai          Retrieval, grounding, generation, ingestion, caching
├── ingestion   How an upload reaches the pipeline (in-process or Kafka)
├── audit       The audit trail
├── ratelimit   Redis-backed request limiting
├── dashboard   Counts for the signed-in organization
├── config      Security, Kafka, async, OpenAPI, properties
└── common      Error shape and exception handling
```

---

## Technology stack

| Component | Choice | Why this one |
| --- | --- | --- |
| Language | Java 21 | LTS; compiled with `--release 21` |
| Framework | Spring Boot 3.5.16 | The line Spring AI 1.1.x is built against |
| AI | Spring AI 1.1.8 | Provider-agnostic `ChatClient`, `EmbeddingModel`, `VectorStore` |
| Model | Google Gemini via `spring-ai-starter-model-google-genai` | Authenticates with a plain API key; no GCP project needed |
| Database | PostgreSQL 16 + pgvector | One database for both relational data and vectors |
| Migrations | Flyway | The schema is explicit and reviewable |
| Auth | Spring Security + JJWT 0.12.7 | Stateless bearer tokens |
| Cache / limits | Redis 7 | Shared across instances |
| Messaging | Apache Kafka 3.9 (KRaft) | Durable ingestion queue, optional |
| Documents | PDFBox 3, Apache POI 5 | PDFBox exposes per-page text, which is what makes page citations real |
| API docs | springdoc-openapi 2.9 | Swagger UI at `/swagger-ui.html` |
| Frontend | Next.js 16, React 19, Tailwind 4, Axios | Reuses the frontend already in this repository |
| Tests | JUnit 5, AssertJ, Mockito, Testcontainers | Integration tests run against real pgvector |

**Spring Boot 3.5 rather than 4.x** — Spring AI 2.0 requires Spring Boot 4.1 and
Spring Security 7, whose configuration DSL changed substantially. The 3.5 line
paired with Spring AI 1.1.8 is far better documented, which matters for a project
meant to be read and explained.

**Java without Lombok** — every constructor and accessor is visible to a reader,
and there is no annotation processor to go wrong on a newer JDK.

---

## How retrieval-augmented answering works here

### Ingesting a document

```
Upload (ADMIN)
   ↓  validated: extension allowlist, size limit, filename reduced to a bare name
Stored on disk as <storage-root>/<tenantId>/<documentId>.<ext>
   ↓  the stored name is generated, never taken from the upload
Document row committed with status PENDING
   ↓
Dispatched to ingestion (in-process pool, or a Kafka event)
   ↓
Text extracted ── PDF: page by page, via PDFBox
   │              DOCX: whole document, via POI
   │              TXT:  whole file
   ↓
Each page split into ~400-token chunks
   ↓
Every chunk tagged: tenantId, documentId, fileName, visibility, pageNumber?
   ↓
Embedded with Gemini in batches of 50, stored in pgvector
   ↓
Status PROCESSED, with the chunk count and the real page count
```

Splitting **per page** rather than across the whole document is what keeps a
chunk attached to the page it came from.

### Answering a question

```
POST /api/chat  { question }
   ↓
Rate limit checked in Redis, keyed by user
   ↓
Tenant and role read from the signed JWT — never from the request body
   ↓
Filter built:  tenantId == '<tenant>' AND visibility IN (<levels this role may read>)
   ↓
Spring AI compiles that filter into a SQL predicate on the vector table
   ↓
pgvector runs the similarity search WITH the filter applied
   → rows from other tenants are never read, never scored, never returned
   ↓
No passages survive?  →  return "not found in your documents"; no model call at all
   ↓
Passages numbered [1], [2], … and placed in the prompt with their file names
   ↓
Gemini generates an answer, instructed to use only these passages and cite them
   ↓
Citations built from the retrieved chunks' metadata — never from the model's text
   ↓
Both turns saved to the conversation; the query is audited
```

Two properties worth calling out:

**The model is never asked to respect authorization.** It cannot leak another
tenant's data because it is never shown any. Authorization happens in the
database, one step earlier.

**Citations are not generated by the model.** Every file name, page number, and
excerpt is copied from the metadata of a chunk that was actually retrieved. The
only thing read from the model's output is which bracketed numbers it used, which
marks each source as cited or merely consulted. A page number the model writes in
prose is ignored.

---

## Multi-tenancy

A single schema with a `tenant_id` column on every tenant-owned table. Not
schema-per-tenant: that multiplies migration and connection-pool complexity for a
first version, and this model is sufficient when the rule is applied without
exception.

The rule, applied everywhere:

1. **The tenant comes from the token.** `AuthenticatedUser` carries `tenantId`,
   derived from a signed claim. No service method accepts a tenant from a caller.
2. **Every repository method names the tenant.** Lookups are
   `findByIdAndTenantId`, not `findById`. A valid identifier from another
   organization returns empty and becomes a 404 — indistinguishable from an
   identifier that does not exist anywhere.
3. **The vector search is filtered in SQL.** The tenant predicate is part of the
   query, not a post-filter over results.
4. **Even deletes name the tenant.** Removing a document's chunks filters on
   tenant as well as document id, so no statement touches vector rows without
   naming a tenant.

The one deliberate exception is `findByEmail`, which backs login. Authentication
by definition runs before a tenant is known; the tenant is then read from the row
it returns.

---

## Security

| Concern | How it is handled |
| --- | --- |
| Passwords | BCrypt (Spring Security default, strength 10). Input capped at 72 characters, because BCrypt silently ignores bytes past that |
| Sessions | Stateless HS256 JWT carrying user, tenant, and role. Signing key must be ≥ 256 bits or startup fails |
| Live account state | Every authenticated request re-reads the user row, so disabling an account or changing a role takes effect on the next request rather than at token expiry |
| Account enumeration | Unknown email and wrong password return an identical 401. An unknown email is still checked against a dummy hash so the two take comparable time |
| Authorization | `@PreAuthorize` on controller methods, next to the code it guards |
| Cross-tenant reads | 404, identical to "does not exist" |
| Insufficient role, same tenant | 403 — within one organization it is more useful to learn access is restricted |
| Upload safety | Extension allowlist; size limit; filename reduced to a bare name; file written under a generated name |
| Path traversal | Resolved paths are checked to be inside the storage root |
| Error responses | One `ApiError` shape. Stack traces, SQL text, and provider messages stay in the log |
| Prompt injection via documents | Document text is passed with a no-op template renderer, so braces in company documents cannot be interpreted as prompt placeholders |
| Secrets | Read from the environment. No key appears in source, config, logs, or frontend code |
| Audit privacy | Question and answer text are **not** stored by default — see below |

### Why the audit log does not store questions by default

AI questions and answers routinely contain confidential passages from company
documents. Copying them into a long-lived audit table duplicates that data
somewhere with different retention and different access controls, widening the
blast radius of any future leak.

What is always recorded is enough to investigate misuse: who asked, when, in
which tenant, how long the question was, whether it was grounded, and which
documents were cited. Set `AUDIT_STORE_QUERY_TEXT=true` to retain the full text
when your compliance requirements call for it.

### Known trade-off: token storage

The token is kept in `localStorage`, so a cross-site scripting flaw could read
it. An `httpOnly` cookie would not be readable by script, but requires the API to
set cookies and to carry CSRF protection, which the stateless bearer-token design
avoids. This is stated rather than hidden; moving to cookies is listed under
future improvements.

---

## Database design

Schema created by Flyway in
[`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql).

```
tenants
  id, name, slug (unique), created_at

users
  id, tenant_id → tenants, name, email (unique), password_hash,
  role, enabled, created_at

documents
  id, tenant_id → tenants, file_name, storage_path, content_type, size_bytes,
  uploaded_by → users, status, visibility, chunk_count, page_count,
  failure_reason, created_at, processed_at

conversations
  id, tenant_id → tenants, user_id → users, title, created_at, updated_at

messages
  id, conversation_id → conversations, role, content, sources_json, created_at

audit_logs
  id, tenant_id, user_id, action, resource_id, metadata_json, ip_address, created_at

vector_store            ← created and indexed by Spring AI, not by Flyway
  id, content, metadata (jsonb), embedding vector(768)
```

Notes on specific choices:

- **`ddl-auto: validate`.** Flyway owns the schema; Hibernate only checks the
  entities match it, turning a mapping mistake into a startup failure.
- **`vector_store` is left to Spring AI.** Writing that DDL by hand would risk
  drifting from what the library expects across versions.
- **Embeddings are 768 dimensions, not 3072.** `gemini-embedding-001` returns
  3072 by default, but a pgvector HNSW index supports at most 2000 — the index
  build would fail. 768 is a supported truncation and keeps HNSW usable.
- **`audit_logs` has no foreign keys.** Some events happen before an identity
  exists (a failed login for an unknown address), and the record should outlive
  the user it refers to.
- **Citations live in a JSON column on `messages`.** They are always read and
  written with their message and never queried on their own, so a child table
  would add a join and buy nothing.

---

## API endpoints

Interactive documentation runs at <http://localhost:8080/swagger-ui.html>.

### Authentication

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | public | Create an organization and its first administrator |
| `POST` | `/api/auth/login` | public | Exchange email and password for a token |
| `GET` | `/api/auth/me` | any | The current user |

### Users

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/users` | ADMIN, MANAGER | List users in your organization |
| `GET` | `/api/users/{id}` | ADMIN, MANAGER | One user |
| `POST` | `/api/users` | ADMIN | Add a user to your organization |
| `PUT` | `/api/users/{id}` | ADMIN | Update name, role, or enabled state |
| `DELETE` | `/api/users/{id}` | ADMIN | Remove a user |

### Documents

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/documents/upload` | ADMIN | Upload a PDF, DOCX, or TXT (returns `202`) |
| `GET` | `/api/documents` | any | Documents your role may read |
| `GET` | `/api/documents/{id}` | any | One document's metadata |
| `DELETE` | `/api/documents/{id}` | ADMIN | Delete the row, the file, and the embeddings |

### Chat

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/chat` | any | Ask a question; returns the answer and its sources |
| `GET` | `/api/conversations` | any | Your conversations |
| `GET` | `/api/conversations/{id}` | any | One conversation with its full history |
| `DELETE` | `/api/conversations/{id}` | any | Delete one of your conversations |

### Audit and dashboard

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/audit-logs` | ADMIN | Audit records, optionally filtered by `action` |
| `GET` | `/api/dashboard/summary` | any | Counts for your organization |

### Error shape

Every failure returns the same body:

```json
{
  "timestamp": "2026-01-15T10:30:00Z",
  "status": 403,
  "code": "FORBIDDEN",
  "message": "You do not have permission to perform this action.",
  "path": "/api/documents/upload"
}
```

Validation failures add a `fieldErrors` object mapping each field to its
messages.

---

## Running it without Docker or an API key

There is a `demo` profile that runs the entire application with no Docker, no
PostgreSQL, no Redis, and no Gemini key:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Then start the frontend and open <http://localhost:3000>.

Three things are substituted, and **nothing else**:

| Real | Demo substitute |
| --- | --- |
| PostgreSQL | H2, in memory |
| pgvector | `SimpleVectorStore`, Spring AI's in-memory store |
| Gemini chat + embeddings | `ExtractiveChatModel` and `LocalEmbeddingModel` |

Everything else runs for real: registration, login, JWT, roles, tenant isolation,
upload, PDF/DOCX/TXT extraction, chunking, retrieval **with the same
authorization filter**, citation building, conversations, and the audit trail.

Two limits, stated plainly because they are easy to mistake for the real thing:

- **The embeddings are not semantic.** `LocalEmbeddingModel` hashes words into
  buckets. It matches shared vocabulary, so it cannot tell that "holiday" and
  "annual leave" mean the same thing. A real embedding model can.
- **No language model is called.** `ExtractiveChatModel` picks the sentences from
  the retrieved passages that overlap the question and returns them verbatim.
  Every reply ends with a line saying so, and the application logs a banner at
  startup, so an extract can never be mistaken for a generated answer.

The demo profile is for seeing the UI and the plumbing work. For real answers,
supply a key and run without it.

## Local setup

### Prerequisites

- JDK 21 or newer
- Maven 3.9+
- Docker Desktop (for PostgreSQL, Redis, and the integration tests)
- Node.js 20+
- A Google Gemini API key from [Google AI Studio](https://aistudio.google.com/)

### 1. Configuration

```bash
cp .env.example .env
```

Open `.env` and set:

- `GEMINI_API_KEY` — your key. **This file is git-ignored and must stay local.**
- `POSTGRES_PASSWORD` — any local password.
- `JWT_SECRET` — generate one:

```bash
openssl rand -base64 48
```

### 2. Start the infrastructure

```bash
docker compose up -d postgres redis
```

### 3. Run the backend

```bash
cd backend && mvn spring-boot:run
```

Flyway applies the schema and Spring AI creates the vector table on first start.
The API comes up on <http://localhost:8080>.

### 4. Run the frontend

```bash
cd frontend && cp .env.local.example .env.local && npm install && npm run dev
```

Open <http://localhost:3000>, register an organization, upload a document, and
ask a question about it.

---

## Environment variables

Every value is read from the environment. Nothing is hardcoded, and no real
secret is committed. See [`.env.example`](.env.example) for the full annotated
list.

| Variable | Default | Purpose |
| --- | --- | --- |
| `GEMINI_API_KEY` | *(required)* | Gemini API key. Backend only — never in frontend code |
| `GEMINI_CHAT_MODEL` | `gemini-2.5-flash` | Chat model |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | Embedding model |
| `GEMINI_EMBEDDING_DIMENSIONS` | `768` | Must match `EMBEDDING_DIMENSIONS` |
| `EMBEDDING_DIMENSIONS` | `768` | pgvector column width |
| `POSTGRES_HOST` / `PORT` / `DB` / `USER` | `localhost` / `5432` / `knowledge_base` / `knowledge` | Database connection |
| `POSTGRES_PASSWORD` | *(required)* | Database password |
| `JWT_SECRET` | *(required)* | Base64 signing key, ≥ 32 bytes decoded |
| `JWT_EXPIRATION_MINUTES` | `120` | Token lifetime |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Only used when `INGESTION_MODE=KAFKA` |
| `INGESTION_MODE` | `IN_PROCESS` | `IN_PROCESS` or `KAFKA` |
| `STORAGE_ROOT` | `./storage` | Where uploaded files are written |
| `ANSWER_CACHE_ENABLED` | `false` | Cache generated answers in Redis |
| `AUDIT_STORE_QUERY_TEXT` | `false` | Retain question and answer text in the audit log |
| `RATE_LIMIT_AI_REQUESTS` | `20` | AI questions per user per window |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | Window length |
| `RAG_TOP_K` | `6` | Passages retrieved per question |
| `RAG_SIMILARITY_THRESHOLD` | `0.35` | Minimum similarity for a passage to be used |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated allowed origins |

---

## Running with Docker

```bash
cp .env.example .env      # fill in GEMINI_API_KEY, JWT_SECRET, POSTGRES_PASSWORD
docker compose up -d
```

This starts PostgreSQL with pgvector, Redis, and the backend. Compose refuses to
start if a required secret is missing, rather than starting a broken container.

Kafka sits behind a profile, because the default ingestion mode does not need it:

```bash
docker compose --profile kafka up -d
```

To use it, also set `INGESTION_MODE=KAFKA` in `.env`.

The frontend is intentionally not containerized — it is a development server that
benefits from hot reload, so it is run with `npm run dev`.

### Why Kafka is optional

Both ingestion modes return from the upload immediately and leave the document
`PENDING`; embedding a large PDF takes far too long to hold an HTTP request open
for. The difference is durability:

- **`IN_PROCESS`** keeps the queue in memory. Work queued when the process stops
  is lost and its document stays `PENDING`.
- **`KAFKA`** persists the event, survives a restart, and lets ingestion be
  scaled separately from the API.

Kafka is here to solve that specific problem, and the application was built and
tested without it first.

---

## Testing

```bash
cd backend && mvn test
```

**49 tests, all passing.** 31 need nothing external; 18 use Testcontainers.

Without a Docker daemon the 18 container-backed tests **skip** rather than fail,
so a skipped result honestly reports that they did not run. The remaining 31 —
including seven tenant-isolation tests — still run, so the central claim of this
project is checked on every `mvn test` regardless.

The container is a never-stopped singleton shared by all integration classes.
That matters: the `@Testcontainers` extension stops a static container when its
declaring class finishes, which left the second class connecting to a dead port.

| Suite | Covers |
| --- | --- |
| `TenantIsolationIntegrationTest` | The central claim, from nine angles, against real pgvector |
| `SimpleVectorStoreTenantIsolationTest` | The same claim in the in-memory store, with no Docker needed |
| `AuthenticationIntegrationTest` | Registration, login, token rejection, role enforcement |
| `JwtServiceTest` | Round-trip, forged signature, tampered payload, weak key |
| `RagServiceAuthorizationFilterTest` | Filter shape for every role |
| `DocumentVisibilityTest` | Role-to-visibility mapping, exhaustively |
| `DocumentFileNameTest` | Path traversal reduction, extension parsing |

### The tenant isolation tests

These run against **real PostgreSQL with pgvector** in a container, so the filter
is genuinely translated into SQL and genuinely applied by the database. Only the
two Gemini calls are replaced:

- `DeterministicEmbeddingModel` — a bag-of-words hash. Texts sharing vocabulary
  land close together, texts sharing none land far apart. Not a semantic model
  and not pretending to be; it exists so retrieval is deterministic and offline.
- `RecordingChatModel` — records every prompt it receives.

Recording the prompt enables the strongest available assertion. Rather than
checking that an answer *looks* right, the test asserts that another tenant's
text **was never placed in the prompt at all**:

```java
assertThat(recorder.lastPrompt())
        .doesNotContain("Globex")
        .doesNotContain("eight weeks")
        .contains("Acme");
```

Two organizations are set up with deliberately similar documents — both about
parental leave, both mentioning payroll — so that without the filter, results
from both would score highly. The tests then verify that a vector search returns
only the caller's tenant, that a document id from another tenant returns 404,
that an employee cannot retrieve or read an admins-only document, and that
listing users and documents never crosses an organization boundary.

---

## What is and is not implemented

Stated plainly, because a claim in a README is worthless if it has not been run.

### Verified by running it

- **All 49 tests pass**, including the nine tenant-isolation tests against real
  PostgreSQL with pgvector in a container.
- **The full flow was exercised end to end in demo mode**: register an
  organization, upload a document, watch it index, ask a question, get an answer
  with a citation carrying a real similarity score, and read the audit trail.
- **Tenant isolation was confirmed against a live server**, not only in tests. A
  second organization fetching the first's document by id got a 404, saw zero
  documents, and its question returned `grounded: false` with no sources.
- **Role enforcement was confirmed live.** An employee got 403 on the audit log
  and on user creation, saw one document instead of two, and a question about the
  admins-only document was answered from the handbook alone, with no leak.
- **Audit privacy was confirmed.** `AI_QUERY` records store `questionLength`,
  `grounded`, `sourceCount` and `citedDocumentIds` — and no question or answer
  text.
- The frontend typechecks and builds; all six routes compile and were walked in a
  browser.
- Dependency versions and Spring AI property names were read from the published
  artifacts, not from memory.

Two bugs were found by running the UI and have been fixed. Both had the same
cause: the response DTOs use `@JsonInclude(NON_NULL)`, so a null `pageNumber` or
`pageCount` is **omitted** from the JSON rather than sent as `null`, and a strict
`!== null` check in TypeScript treated absent as present. The citation line
rendered "page " with nothing after it, and the document list showed "undefined
pages". Both now compare with `!= null`.

### Written but not yet executed end to end

- **A live Gemini call.** No API key has been used. The ingestion and answering
  paths are written against Spring AI's verified API and exercised with offline
  stand-ins, but not against the real service.
- **`INGESTION_MODE=KAFKA`.** The producer, consumer, topic configuration, and
  serializers are written, but the Kafka path has not been run.
- **The Docker images.** `docker-compose.yml` and the backend `Dockerfile` are
  written but have not been built.

### Deliberately not built

- **OCR.** A scanned, image-only PDF is rejected with a message saying so,
  rather than being indexed as an empty document.
- **Page numbers for DOCX and TXT.** Those formats have no pages — DOCX
  pagination is decided by the renderer. Citations report "page not applicable"
  instead of inventing one.
- **Refresh tokens.** Sessions last for the token's lifetime, then require a new
  sign-in.
- **Google OAuth.** The repository's previous frontend signed in through
  NextAuth with Google. That was replaced by the backend's JWT, because social
  identity has no server-side support here and two parallel auth paths would be
  worse than one.
- **Document versioning, streaming responses, WebSockets, Kubernetes.**

### No performance claims

This README contains no throughput, latency, or concurrency numbers, because none
have been measured. Any figure here would be invented.

---

## Future improvements

Roughly in order of value:

1. **Run the integration tests in CI**, so tenant isolation is verified on every
   change rather than on demand.
2. **Move the token to an `httpOnly` cookie** with CSRF protection, closing the
   XSS exposure noted above.
3. **Refresh tokens**, so sessions can be short-lived without constant re-login.
4. **Retry and dead-letter handling for Kafka ingestion**, so a transient
   embedding failure is retried rather than recorded as permanent.
5. **Hybrid retrieval** — combine vector similarity with PostgreSQL full-text
   search, which handles exact terms like policy numbers far better than
   embeddings do.
6. **Re-ranking** the retrieved passages before they reach the prompt.
7. **Streaming answers**, so the first tokens appear immediately.
8. **Object storage** for uploaded files, replacing the single
   `DocumentStorageService` class.
9. **Measure something**, then write the numbers here.
