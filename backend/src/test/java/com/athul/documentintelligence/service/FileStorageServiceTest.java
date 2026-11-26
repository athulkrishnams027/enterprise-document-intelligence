package com.athul.documentintelligence.service;

import com.athul.documentintelligence.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesReadsDeletesAndBlocksPathTraversal() {
        FileStorageService storage = new FileStorageService(tempDir.toAbsolutePath().normalize());
        UUID ownerId = UUID.randomUUID();
        byte[] payload = "Secure document payload".getBytes(StandardCharsets.UTF_8);

        String key = storage.store(ownerId, "txt", payload);
        assertThat(storage.readBytes(key)).isEqualTo(payload);

        assertThatThrownBy(() -> storage.resolveSafe("../../etc/passwd"))
                .isInstanceOf(BadRequestException.class);

        storage.deleteQuietly(key);
        assertThat(storage.resolveSafe(key).toFile()).doesNotExist();
    }
}
