package com.athul.documentintelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized document filesystem storage properties.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String rootPath,
        long maxFileSizeBytes
) {
}
