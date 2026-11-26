package com.athul.documentintelligence.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Initializes and normalizes the private document storage root outside public web directories.
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    public Path documentStorageRoot(StorageProperties properties) throws IOException {
        Path root = Paths.get(properties.rootPath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Initialized document storage root at {}", root);
        return root;
    }
}
