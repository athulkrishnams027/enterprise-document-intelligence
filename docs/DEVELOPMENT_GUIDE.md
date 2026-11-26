# Development & Verification Guide

## Running Locally Without Full Docker Stack

### 1. Start PostgreSQL + pgvector & Ollama
```bash
docker compose up -d postgres ollama
docker exec -it docintel-ollama ollama pull qwen2.5:7b
docker exec -it docintel-ollama ollama pull nomic-embed-text
```

### 2. Run Backend (Java 21)

If Java 21 is installed natively on your host:
```bash
cd backend
POSTGRES_PORT=5433 mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

If your host default JDK is < 21, run Maven via the official Java 21 Docker image while sharing your local Maven cache:
```bash
docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$HOME/.m2":/root/.m2 \
  -v "$PWD":/workspace \
  -w /workspace/backend \
  maven:3.9-eclipse-temurin-21 mvn test
```

### 3. Run Frontend (Node.js 20+)
```bash
cd frontend
npm install
npm run dev
```

## Building Production Artifacts

```bash
# Backend JAR
cd backend && mvn clean package

# Frontend static bundle
cd frontend && npm run build
```

## Full Docker Stack Deployment

```bash
cp .env.example .env
# Edit .env with strong POSTGRES_PASSWORD and JWT_SECRET
docker compose up -d --build
```

## Automated Test Suite

- `JwtServiceTest`, `AuthServiceTest`: JWT signing/tamper rejection, BCrypt registration & duplicate rejection.
- `DocumentValidationServiceTest`, `FileStorageServiceTest`: PDF/DOCX/TXT magic-byte + Tika verification, path-traversal blocking.
- `DocumentExtractionServiceTest`, `DocumentChunkingServiceTest`: Multi-page PDFs, empty page skipping, encrypted/corrupt PDF rejection, DOCX sections, UTF-8 TXT validation, token-bounded overlapping chunks.
- `EmbeddingServiceTest`, `RagPromptBuilderTest`, `CitationServiceTest`: Dimension validation, context budget, prompt injection system instructions, null vs zero page numbers.
- `AuthControllerIntegrationTest`, `DocumentControllerIntegrationTest`, `PgVectorStoreIntegrationTest`, `RagAndConversationIntegrationTest`, `EnterpriseHardeningIntegrationTest`: Full end-to-end verification using real `pgvector/pgvector:pg16` Testcontainers.
