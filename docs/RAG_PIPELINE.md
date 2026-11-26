# Retrieval-Augmented Generation (RAG) & Ingestion Pipeline

## 1. Document Ingestion Workflow

```mermaid
flowchart TD
    UP["POST /api/v1/documents/upload"] --> VAL["DocumentValidationService<br/>Extension + Magic Bytes + Tika MIME + SHA-256"]
    VAL --> DUP{"Checksum exists<br/>for ownerId?"}
    DUP -->|Yes| ERR409["409 DUPLICATE_RESOURCE"]
    DUP -->|No| SAVE["Store binary in private storage +<br/>Save DocumentMetadata (UPLOADED)"]
    SAVE -->|"Commit TX"| EVT["DocumentIngestionTrigger<br/>(@TransactionalEventListener AFTER_COMMIT + @Async)"]
    EVT --> CLAIM["Claim Document (PROCESSING)"]
    CLAIM --> EXT["DocumentExtractionService<br/>PDFBox (1-based pages) / POI DOCX / UTF-8 TXT"]
    EXT --> CHK["DocumentChunkingService<br/>jtokkit CL100K (800 target / 150 overlap)"]
    CHK --> DEL["Delete stale vectors by documentId"]
    DEL --> EMB["PgVectorStore.add(chunks)<br/>EmbeddingModel -> vector_store (HNSW)"]
    EMB -->|Success| DONE["Mark COMPLETED + Log Audit"]
    EMB -->|Failure| FAIL["Clean partial vectors -> Mark FAILED"]
```

## 2. Chunking Strategy

Implemented in `DocumentChunkingService` using `jtokkit` (`CL100K_BASE` token counter):

1. **Section/Page Preservation**: PDFs are extracted page-by-page (`pageNumber >= 1`, skipping blank pages while keeping true page numbers). DOCX documents preserve heading boundaries (`sectionTitle`, `pageNumber = null`). TXT files validate strict UTF-8 encoding (`pageNumber = null`).
2. **Paragraph & Sentence Integrity**: Sections are split on blank lines (`\n\s*\n`). Paragraphs exceeding `targetTokens` (default `800`) are split along sentence boundaries (`(?<=[.!?])\s+`), falling back to word boundaries only if a single sentence exceeds `targetTokens`.
3. **Configurable Overlap**: Consecutive chunks within a section carry trailing units up to `overlapTokens` (default `150`) to preserve cross-boundary semantic context.
4. **Deterministic Identifiers**: Each chunk receives `chunkId = "chunk-<index>"` and a deterministic UUID (`UUID.nameUUIDFromBytes(documentId + ":" + chunkIndex)`) plus `contentHash` (SHA-256) to prevent duplicate chunk embeddings within a document.

## 3. Retrieval & Grounded Question Answering Workflow

```mermaid
sequenceDiagram
    actor User
    participant API as RagController / RagService
    participant Conv as ConversationService
    participant VS as VectorSearchService (pgvector)
    participant Prompt as RagPromptBuilder
    participant LLM as Spring AI ChatClient

    User->>API: POST /api/v1/rag/ask or /conversations/{id}/chat
    API->>API: Resolve authenticated userId from SecurityContext
    API->>Conv: Verify conversation/document ownership + load bounded history
    API->>Prompt: Build contextual retrieval query (for short/referential follow-ups)
    API->>VS: similaritySearch(query, ownerId, documentId, topK, threshold)
    VS-->>API: Authorized chunks matching metadata filter
    alt No chunks retrieved
        API-->>User: Insufficient information response (grounded=false, sources=[], LLM not called)
    else Chunks retrieved
        API->>Prompt: Select chunks within maxContextChars & wrap in <document_context>
        API->>LLM: System prompt (anti-injection + grounding rules) + User prompt
        LLM-->>API: Grounded answer + token usage
        API->>API: Build SourceCitationResponse strictly from supplied chunks
        API->>Conv: Persist USER + ASSISTANT messages & JSONB citations
        API->>API: Record ai_audit_logs
        API-->>User: RagAnswerResponse (answer + verified sources + timing)
    end
```

## 4. Prompt Injection & Hallucination Defenses

- **Authorization at Retrieval Time**: `ownerId` and optional `documentId` filters are compiled directly into the `PgVectorStore` SQL query (`FilterExpressionBuilder`), never filtered after global retrieval.
- **Zero-Context Guard**: When similarity search returns zero chunks above `similarityThreshold`, `RagService` returns an explicit insufficient-information response without invoking the LLM.
- **Untrusted Context Isolation**: Retrieved text is delimited inside `<document_context>...</document_context>` and the system prompt explicitly forbids executing instructions found inside document excerpts.
- **No Fabricated Citations**: `CitationService` derives citations strictly from the chunks actually passed into the context budget, keeping `pageNumber = null` for DOCX/TXT rather than reporting `0`.
