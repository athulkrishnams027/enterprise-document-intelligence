package com.athul.documentintelligence.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum SupportedDocumentType {
    PDF("pdf", "application/pdf", Set.of("application/pdf")),
    DOCX(
            "docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml"
            )
    ),
    TXT("txt", "text/plain", Set.of("text/plain"));

    private final String extension;
    private final String canonicalMimeType;
    private final Set<String> acceptedDetectedMimeTypes;

    public static Optional<SupportedDocumentType> fromExtension(String ext) {
        if (ext == null) {
            return Optional.empty();
        }
        String normalized = ext.trim().toLowerCase();
        for (SupportedDocumentType type : values()) {
            if (type.extension.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
