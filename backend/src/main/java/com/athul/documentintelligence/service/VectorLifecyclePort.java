package com.athul.documentintelligence.service;

import java.util.UUID;

/**
 * Abstraction for removing or inspecting vector records associated with a document.
 */
public interface VectorLifecyclePort {

    void deleteByDocumentId(UUID documentId);
}
