package com.athul.documentintelligence.dto.response;

import java.util.Map;

public record AdminDashboardResponse(
        long totalUsers,
        long totalDocuments,
        long completedDocuments,
        long processingDocuments,
        long failedDocuments,
        long totalConversations,
        long totalAiRequests,
        long failedAiRequests,
        long totalTokensConsumed,
        Map<String, Object> activeModelInfo
) {
}
