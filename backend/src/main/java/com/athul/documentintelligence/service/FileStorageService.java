package com.athul.documentintelligence.service;

import com.athul.documentintelligence.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Stores and retrieves uploaded document binaries securely inside the configured private
 * storage root, enforcing strict path-traversal prevention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final Path documentStorageRoot;

    public String store(UUID ownerId, String extension, byte[] content) {
        String safeExtension = extension.replaceAll("[^a-zA-Z0-9]", "");
        String relativeKey = ownerId + "/" + UUID.randomUUID() + "." + safeExtension;
        Path targetPath = resolveSafe(relativeKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content, StandardOpenOption.CREATE_NEW);
            log.debug("Stored document binary at relativeKey={} sizeBytes={}", relativeKey, content.length);
            return relativeKey;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist document file to storage", ex);
        }
    }

    public byte[] readBytes(String storedRelativeKey) {
        Path path = resolveSafe(storedRelativeKey);
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read stored document binary", ex);
        }
    }

    public Path resolveSafe(String storedRelativeKey) {
        if (storedRelativeKey == null || storedRelativeKey.isBlank() || storedRelativeKey.contains("..")) {
            throw new BadRequestException("INVALID_STORAGE_PATH", "Invalid stored file reference");
        }
        Path resolved = documentStorageRoot.resolve(storedRelativeKey).normalize();
        if (!resolved.startsWith(documentStorageRoot)) {
            throw new BadRequestException("PATH_TRAVERSAL_BLOCKED", "Attempted access outside document storage root");
        }
        return resolved;
    }

    public void deleteQuietly(String storedRelativeKey) {
        if (storedRelativeKey == null || storedRelativeKey.isBlank()) {
            return;
        }
        try {
            Path resolved = resolveSafe(storedRelativeKey);
            Files.deleteIfExists(resolved);
        } catch (Exception ex) {
            log.warn("Could not delete stored binary key={}: {}", storedRelativeKey, ex.getMessage());
        }
    }
}
