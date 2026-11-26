package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.exception.AiProviderException;
import com.athul.documentintelligence.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages PgVectorStore persistence, user- and document-scoped similarity search,
 * and lifecycle cleanup. Authorization filters are pushed directly into PgVectorStore
 * database queries rather than filtering globally fetched chunks in memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService implements VectorLifecyclePort {

    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_OWNER_ID = "ownerId";
    public static final String META_CHUNK_ID = "chunkId";
    public static final String META_CHUNK_INDEX = "chunkIndex";
    public static final String META_FILENAME = "originalFilename";
    public static final String META_PAGE_NUMBER = "pageNumber";
    public static final String META_SECTION_TITLE = "sectionTitle";
    public static final String META_CONTENT_HASH = "contentHash";

    private final VectorStore vectorStore;
    private final AiProperties aiProperties;

    public void indexChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<Document> documents = chunks.stream()
                .map(this::toSpringAiDocument)
                .toList();

        long start = System.currentTimeMillis();
        try {
            vectorStore.add(documents);
            log.info("Indexed {} chunks into pgvector in {} ms",
                    documents.size(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.error("Vector indexing failure for {} chunks: {}", documents.size(), ex.getMessage());
            throw new AiProviderException(
                    "VECTOR_INDEXING_ERROR",
                    "Failed to embed or store document chunks in vector database",
                    ex
            );
        }
    }

    public List<Document> similaritySearch(
            String query,
            UUID ownerId,
            UUID documentId,
            Integer topKOverride,
            Double similarityThresholdOverride
    ) {
        int topK = topKOverride != null ? topKOverride : aiProperties.rag().topK();
        double threshold = similarityThresholdOverride != null
                ? similarityThresholdOverride
                : aiProperties.rag().similarityThreshold();

        Filter.Expression filterExpression = buildAuthorizationFilter(ownerId, documentId);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filterExpression)
                .build();

        long start = System.currentTimeMillis();
        try {
            List<Document> results = vectorStore.similaritySearch(request);
            log.debug("Vector search ownerId={} documentId={} hits={} durationMs={}",
                    ownerId, documentId, results.size(), System.currentTimeMillis() - start);
            return results;
        } catch (Exception ex) {
            log.error("Vector similarity search failed ownerId={}: {}", ownerId, ex.getMessage());
            throw new AiProviderException(
                    "VECTOR_SEARCH_ERROR",
                    "Vector search failed; verify AI embedding service and pgvector availability",
                    ex
            );
        }
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            Filter.Expression filter = b.eq(META_DOCUMENT_ID, documentId.toString()).build();
            vectorStore.delete(filter);
            log.info("Deleted vector embeddings for documentId={}", documentId);
        } catch (Exception ex) {
            log.error("Failed to delete vectors for documentId={}: {}", documentId, ex.getMessage());
            throw new AiProviderException(
                    "VECTOR_DELETE_ERROR",
                    "Failed to delete associated vector records for document",
                    ex
            );
        }
    }

    private Filter.Expression buildAuthorizationFilter(UUID ownerId, UUID documentId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op ownerFilter = b.eq(META_OWNER_ID, ownerId.toString());
        if (documentId != null) {
            return b.and(ownerFilter, b.eq(META_DOCUMENT_ID, documentId.toString())).build();
        }
        return ownerFilter.build();
    }

    private Document toSpringAiDocument(DocumentChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_DOCUMENT_ID, chunk.documentId().toString());
        metadata.put(META_OWNER_ID, chunk.ownerId().toString());
        metadata.put(META_CHUNK_ID, chunk.chunkId());
        metadata.put(META_CHUNK_INDEX, chunk.chunkIndex());
        metadata.put(META_FILENAME, chunk.originalFilename());
        if (chunk.pageNumber() != null) {
            metadata.put(META_PAGE_NUMBER, chunk.pageNumber());
        }
        if (chunk.sectionTitle() != null && !chunk.sectionTitle().isBlank()) {
            metadata.put(META_SECTION_TITLE, chunk.sectionTitle());
        }
        metadata.put(META_CONTENT_HASH, chunk.contentHash());

        return Document.builder()
                .id(chunk.vectorRecordId().toString())
                .text(chunk.content())
                .metadata(metadata)
                .build();
    }
}
