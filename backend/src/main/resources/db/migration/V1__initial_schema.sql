-- ===========================================================================
-- Enterprise AI Knowledge Base -- initial schema
--
-- Ownership note: this migration creates the application's own tables. The
-- vector table itself (`vector_store`) is created and indexed by Spring AI's
-- PgVectorStore when `spring.ai.vectorstore.pgvector.initialize-schema` is
-- true. It is deliberately left to Spring AI rather than duplicated here, so
-- that the column layout can never drift from what the library expects.
-- ===========================================================================

-- Required by the pgvector store. Created here so the extension exists even
-- when schema initialisation in Spring AI is switched off.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------------------------------------------------------------------
-- Tenants
-- ---------------------------------------------------------------------------
CREATE TABLE tenants (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);

-- ---------------------------------------------------------------------------
-- Users
--
-- Email is unique across the whole system rather than per tenant, because it
-- is the login identifier and login happens before a tenant is known.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name          VARCHAR(200) NOT NULL,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'MANAGER', 'EMPLOYEE'))
);

CREATE INDEX idx_users_tenant ON users (tenant_id);

-- ---------------------------------------------------------------------------
-- Documents
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id             UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    file_name      VARCHAR(500)  NOT NULL,
    storage_path   VARCHAR(1000) NOT NULL,
    content_type   VARCHAR(200)  NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    uploaded_by    UUID          NOT NULL REFERENCES users (id),
    status         VARCHAR(20)   NOT NULL,
    visibility     VARCHAR(30)   NOT NULL,
    chunk_count    INTEGER       NOT NULL DEFAULT 0,
    page_count     INTEGER,
    failure_reason VARCHAR(1000),
    created_at     TIMESTAMPTZ   NOT NULL,
    processed_at   TIMESTAMPTZ,
    CONSTRAINT documents_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT documents_visibility_check
        CHECK (visibility IN ('ALL_EMPLOYEES', 'MANAGERS_AND_ADMINS', 'ADMINS_ONLY'))
);

CREATE INDEX idx_documents_tenant_created ON documents (tenant_id, created_at DESC);
CREATE INDEX idx_documents_tenant_status  ON documents (tenant_id, status);

-- ---------------------------------------------------------------------------
-- Conversations and messages
-- ---------------------------------------------------------------------------
CREATE TABLE conversations (
    id         UUID         PRIMARY KEY,
    tenant_id  UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_conversations_tenant_user ON conversations (tenant_id, user_id, updated_at DESC);

CREATE TABLE messages (
    id              UUID        PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    sources_json    TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT messages_role_check CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, created_at);

-- ---------------------------------------------------------------------------
-- Audit logs
--
-- tenant_id and user_id are nullable because some auditable events occur before
-- an identity is established, such as a failed login for an unknown address.
-- The foreign keys are omitted for the same reason and so that audit history
-- survives the deletion of the user it refers to.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            UUID        PRIMARY KEY,
    tenant_id     UUID,
    user_id       UUID,
    action        VARCHAR(40) NOT NULL,
    resource_id   UUID,
    metadata_json TEXT,
    ip_address    VARCHAR(45),
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_tenant_created ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX idx_audit_tenant_action  ON audit_logs (tenant_id, action, created_at DESC);
