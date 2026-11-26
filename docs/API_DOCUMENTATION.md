# REST API Documentation

Base URL: `/api/v1`

All endpoints except `POST /auth/register`, `POST /auth/login`, and `GET /actuator/health` require `Authorization: Bearer <jwt>`.

## Authentication

| Method | Endpoint | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public | Register a new `USER` account (`201 Created`) |
| `POST` | `/api/v1/auth/login` | Public | Authenticate with email/password and receive JWT (`200 OK`) |
| `POST` | `/api/v1/auth/logout` | Bearer | Revoke current JWT token ID (`jti`) (`200 OK`) |
| `GET` | `/api/v1/auth/me` | Bearer | Return authenticated user profile (`200 OK`) |

## Documents

| Method | Endpoint | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/documents/upload` | Bearer | Upload single PDF/DOCX/TXT (`multipart/form-data`, field `file`) (`201 Created`) |
| `POST` | `/api/v1/documents/upload-multiple` | Bearer | Upload batch (`multipart/form-data`, field `files`) (`201 Created`) |
| `GET` | `/api/v1/documents` | Bearer | Paginated list (`page`, `size`, `sort`, `status`, `search`) (`200 OK`) |
| `GET` | `/api/v1/documents/{id}` | Bearer | Document metadata + processing audit logs (`200 OK`) |
| `GET` | `/api/v1/documents/{id}/status` | Bearer | Lightweight processing status check (`200 OK`) |
| `POST` | `/api/v1/documents/{id}/reprocess` | Bearer | Queue document for re-extraction & re-embedding (`202 Accepted`) |
| `DELETE` | `/api/v1/documents/{id}` | Bearer | Delete document, stored file, and `vector_store` embeddings (`204 No Content`) |

## Conversations & RAG

| Method | Endpoint | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/conversations` | Bearer | Create conversation (`title`, optional `documentId`) (`201 Created`) |
| `GET` | `/api/v1/conversations` | Bearer | Paginated list of user conversations (`200 OK`) |
| `GET` | `/api/v1/conversations/{id}` | Bearer | Get conversation details (`200 OK`) |
| `PATCH` | `/api/v1/conversations/{id}` | Bearer | Rename conversation (`200 OK`) |
| `DELETE` | `/api/v1/conversations/{id}` | Bearer | Delete conversation and messages (`204 No Content`) |
| `GET` | `/api/v1/conversations/{id}/messages` | Bearer | Paginated ordered chat history with citations (`200 OK`) |
| `POST` | `/api/v1/conversations/{id}/chat` | Bearer | Ask question inside conversation (`200 OK`) |
| `POST` | `/api/v1/rag/ask` | Bearer | Ask question (uses `conversationId` or creates new conversation) (`200 OK`) |

### Sample RAG Response (`200 OK`)

```json
{
  "conversationId": "3f90b2f7-8b8a-4d1c-9f25-7a16d2a49f10",
  "messageId": "9a1c3f41-6b22-450b-8c1a-4de9b7b0e112",
  "answer": "Employees receive 24 days of annual leave per calendar year [Source 1].",
  "sources": [
    {
      "documentId": "a22d91f0-31c1-443d-b4c1-11805d2c771a",
      "documentName": "Employee_Handbook.pdf",
      "pageNumber": 12,
      "sectionTitle": "Page 12",
      "chunkId": "chunk-14",
      "relevanceScore": 0.9142,
      "excerpt": "Employees receive 24 days of annual leave per calendar year."
    }
  ],
  "processingTimeMs": 1240,
  "tokenUsage": {
    "promptTokens": 420,
    "completionTokens": 28,
    "totalTokens": 448
  },
  "grounded": true
}
```

## Dashboard & Admin

| Method | Endpoint | Auth | Description |
| --- | --- | --- | --- |
| `GET` | `/api/v1/dashboard/summary` | Bearer | User document/conversation KPIs & recent documents |
| `GET` | `/api/v1/admin/dashboard` | `ADMIN` | System-wide totals, AI metrics, active model info |
| `GET` | `/api/v1/admin/users` | `ADMIN` | Paginated registered users |
| `GET` | `/api/v1/admin/ai-usage` | `ADMIN` | Paginated `ai_audit_logs` |

## Standardized Error Format

All errors return `ApiErrorResponse` (never exposing internal stack traces):

```json
{
  "timestamp": "2026-11-26T06:10:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "UNSUPPORTED_FILE_FORMAT",
  "message": "Unsupported file extension '.exe'. Allowed formats: PDF, DOCX, TXT",
  "path": "/api/v1/documents/upload"
}
```
