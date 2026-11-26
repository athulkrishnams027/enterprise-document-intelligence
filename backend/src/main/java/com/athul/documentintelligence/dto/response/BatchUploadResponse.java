package com.athul.documentintelligence.dto.response;

import java.util.List;

public record BatchUploadResponse(
        List<DocumentResponse> uploaded,
        List<BatchUploadFailure> failures
) {
    public record BatchUploadFailure(
            String filename,
            String errorCode,
            String message
    ) {
    }
}
