# Database Design & Vector Store Lifecycle

## Entity-Relationship Diagram

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : assigned_to
    users ||--o{ documents : owns
    documents ||--o{ document_processing_logs : records
    users ||--o{ conversations : owns
    documents |o--o{ conversations : scopes
    conversations ||--o{ chat_messages : contains
    users ||--o{ ai_audit_logs : triggers
    conversations |o--o{ ai_audit_logs : logs
    documents |o--o{ ai_audit_logs : logs
    documents ||--o{ vector_store : "indexed_into (metadata.documentId)"

    users {
        uuid id PK
        varchar email UK
        varchar full_name
        varchar password_hash
        boolean enabled
        timestamptz created_at
        timestamptz updated_at
    }
    roles {
        uuid id PK
        varchar name UK
        varchar description
    }
    documents {
        uuid id PK
        uuid owner_id FK
        varchar original_filename
        varchar stored_filename UK
        varchar mime_type
        bigint file_size
        varchar file_checksum
        varchar status
        text processing_error
        int total_pages
        int total_chunks
        timestamptz uploaded_at
        timestamptz last_processed_at
    }
    document_processing_logs {
        uuid id PK
        uuid document_id FK
        varchar stage
        varchar status
        text message
        bigint duration_ms
        timestamptz created_at
    }
    conversations {
        uuid id PK
        uuid user_id FK
        uuid document_id FK
        varchar title
        timestamptz created_at
        timestamptz updated_at
    }
    chat_messages {
        uuid id PK
        uuid conversation_id FK
        varchar role
        text content
        jsonb citations_json
        bigint processing_time_ms
        jsonb token_usage_json
        timestamptz created_at
    }
    ai_audit_logs {
        uuid id PK
        uuid user_id FK
        uuid conversation_id FK
        uuid document_id FK
        varchar operation
        varchar provider
        varchar model
        int prompt_tokens
        int completion_tokens
        int total_tokens
        bigint duration_ms
        varchar status
        text error_message
        timestamptz created_at
    }
    vector_store {
        uuid id PK
        text content
        json metadata
        vector_768 embedding
    }
```

## Relationship Between Relational Metadata and `vector_store`

- **Relational Authority**: `documents` is the source of truth for ownership (`owner_id`), deduplication (`UNIQUE (owner_id, file_checksum)`), processing status (`UPLOADED`, `PROCESSING`, `COMPLETED`, `FAILED`, `DELETING`), and page/chunk counts.
- **Vector Representation**: `vector_store` (managed by Spring AI `PgVectorStore` with HNSW cosine index) stores chunk text, `768`-dimensional embeddings, and JSON metadata containing `documentId`, `ownerId`, `chunkId`, `chunkIndex`, `originalFilename`, `pageNumber` (omitted when unavailable), `sectionTitle`, and `contentHash`.
- **Consistent Lifecycle**:
  - A document transitions to `COMPLETED` **only after** `vectorStore.add(...)` succeeds.
  - On failure, partial vectors for `documentId` are cleaned up and the document is marked `FAILED`.
  - On `DELETE /api/v1/documents/{id}` or reprocessing, `VectorSearchService.deleteByDocumentId(documentId)` deletes all matching `vector_store` records via metadata filter before removing/re-indexing relational state.
- **Database-Level Authorization**: Every similarity search supplies `FilterExpressionBuilder` predicates (`ownerId == currentUserId` and optional `documentId == targetDocId`), compiled by `PgVectorStore` directly into PostgreSQL `WHERE` clauses.
