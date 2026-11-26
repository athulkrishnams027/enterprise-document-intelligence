package com.athul.documentintelligence.dto.response;

import java.util.List;

public record UserDashboardResponse(
        long totalDocuments,
        long completedDocuments,
        long processingDocuments,
        long failedDocuments,
        long totalConversations,
        List<DocumentResponse> recentDocuments
) {
}
