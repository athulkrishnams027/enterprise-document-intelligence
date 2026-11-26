-- =============================================================================
-- Enterprise Document Intelligence Platform - Initial Relational & PgVector Schema
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -----------------------------------------------------------------------------
-- 1. Security: Roles & Users
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO roles (name, description)
VALUES
    ('USER', 'Standard authenticated user with isolated document and conversation access'),
    ('ADMIN', 'System administrator with user management and system telemetry access')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- -----------------------------------------------------------------------------
-- 2. Document Management & Processing Lifecycle
-- -----------------------------------------------------------------------------
CREATE TABLE documents (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename  VARCHAR(500) NOT NULL,
    stored_filename    VARCHAR(500) NOT NULL UNIQUE,
    mime_type          VARCHAR(150) NOT NULL,
    file_size          BIGINT NOT NULL,
    file_checksum      VARCHAR(64) NOT NULL,
    status             VARCHAR(30) NOT NULL,
    processing_error   TEXT,
    total_pages        INTEGER,
    total_chunks       INTEGER NOT NULL DEFAULT 0,
    extraction_summary VARCHAR(1000),
    uploaded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_processed_at  TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_documents_owner_checksum UNIQUE (owner_id, file_checksum),
    CONSTRAINT chk_documents_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED', 'DELETING'))
);

CREATE INDEX idx_documents_owner_id ON documents (owner_id);
CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_uploaded_at ON documents (uploaded_at DESC);

CREATE TABLE document_processing_logs (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    stage       VARCHAR(50) NOT NULL,
    status      VARCHAR(30) NOT NULL,
    message     TEXT,
    duration_ms BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_proc_logs_doc_id ON document_processing_logs (document_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 3. Conversations & Chat History
-- -----------------------------------------------------------------------------
CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    title       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_user_updated ON conversations (user_id, updated_at DESC);

CREATE TABLE chat_messages (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id    UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role               VARCHAR(20) NOT NULL,
    content            TEXT NOT NULL,
    citations_json     JSONB,
    processing_time_ms BIGINT,
    token_usage_json   JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_chat_messages_conv_created ON chat_messages (conversation_id, created_at ASC);

-- -----------------------------------------------------------------------------
-- 4. AI Audit & Observability Logs
-- -----------------------------------------------------------------------------
CREATE TABLE ai_audit_logs (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    conversation_id   UUID REFERENCES conversations (id) ON DELETE SET NULL,
    document_id       UUID REFERENCES documents (id) ON DELETE SET NULL,
    operation         VARCHAR(50) NOT NULL,
    provider          VARCHAR(50) NOT NULL,
    model             VARCHAR(120) NOT NULL,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    total_tokens      INTEGER,
    duration_ms       BIGINT NOT NULL,
    status            VARCHAR(30) NOT NULL,
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_audit_logs_created ON ai_audit_logs (created_at DESC);
CREATE INDEX idx_ai_audit_logs_user ON ai_audit_logs (user_id, created_at DESC);
