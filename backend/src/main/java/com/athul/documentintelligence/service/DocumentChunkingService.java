package com.athul.documentintelligence.service;

import com.athul.documentintelligence.config.AiProperties;
import com.athul.documentintelligence.model.DocumentChunk;
import com.athul.documentintelligence.model.ExtractedSection;
import com.athul.documentintelligence.model.ExtractionResult;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sentence- and paragraph-aware chunking service with configurable target token size and overlap.
 * Preserves page numbers, section titles, chunk ordering, and deterministic chunk identifiers.
 */
@Service
public class DocumentChunkingService {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    private final AiProperties aiProperties;
    private final Encoding encoding;

    public DocumentChunkingService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    public List<DocumentChunk> chunk(
            UUID documentId,
            UUID ownerId,
            String originalFilename,
            ExtractionResult extractionResult
    ) {
        int targetTokens = Math.max(50, aiProperties.chunking().targetTokens());
        int overlapTokens = Math.max(0, Math.min(aiProperties.chunking().overlapTokens(), targetTokens - 1));

        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ExtractedSection section : extractionResult.sections()) {
            List<String> units = splitIntoUnits(section.text(), targetTokens);
            List<String> sectionChunks = packUnitsWithOverlap(units, targetTokens, overlapTokens);

            for (String chunkText : sectionChunks) {
                String trimmed = chunkText.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int tokens = countTokens(trimmed);
                String chunkId = "chunk-" + chunkIndex;
                UUID vectorRecordId = UUID.nameUUIDFromBytes(
                        (documentId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)
                );
                String contentHash = sha256Hex(trimmed);

                chunks.add(new DocumentChunk(
                        vectorRecordId,
                        chunkId,
                        documentId,
                        ownerId,
                        originalFilename,
                        section.pageNumber(),
                        section.sectionTitle(),
                        chunkIndex,
                        tokens,
                        contentHash,
                        trimmed
                ));
                chunkIndex++;
            }
        }

        return chunks;
    }

    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokensOrdinary(text);
    }

    private List<String> splitIntoUnits(String text, int targetTokens) {
        List<String> units = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_SPLIT.split(text);
        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isEmpty()) {
                continue;
            }
            if (countTokens(trimmedParagraph) <= targetTokens) {
                units.add(trimmedParagraph);
            } else {
                String[] sentences = SENTENCE_SPLIT.split(trimmedParagraph);
                for (String sentence : sentences) {
                    String trimmedSentence = sentence.trim();
                    if (trimmedSentence.isEmpty()) {
                        continue;
                    }
                    if (countTokens(trimmedSentence) <= targetTokens) {
                        units.add(trimmedSentence);
                    } else {
                        units.addAll(splitOversizedSentenceByWords(trimmedSentence, targetTokens));
                    }
                }
            }
        }
        return units;
    }

    private List<String> splitOversizedSentenceByWords(String sentence, int targetTokens) {
        List<String> parts = new ArrayList<>();
        String[] words = sentence.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && countTokens(candidate) > targetTokens) {
                parts.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private List<String> packUnitsWithOverlap(List<String> units, int targetTokens, int overlapTokens) {
        List<String> packed = new ArrayList<>();
        List<String> currentUnits = new ArrayList<>();
        int currentTokens = 0;

        for (String unit : units) {
            int unitTokens = countTokens(unit);
            if (!currentUnits.isEmpty() && currentTokens + unitTokens > targetTokens) {
                packed.add(String.join("\n\n", currentUnits));
                currentUnits = buildOverlapSeed(currentUnits, overlapTokens);
                currentTokens = countTokens(String.join("\n\n", currentUnits));
            }
            currentUnits.add(unit);
            currentTokens += unitTokens;
        }

        if (!currentUnits.isEmpty()) {
            packed.add(String.join("\n\n", currentUnits));
        }
        return packed;
    }

    private List<String> buildOverlapSeed(List<String> previousUnits, int overlapTokens) {
        if (overlapTokens <= 0 || previousUnits.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> seed = new ArrayList<>();
        int accumulated = 0;
        for (int i = previousUnits.size() - 1; i >= 0; i--) {
            String candidate = previousUnits.get(i);
            int tokens = countTokens(candidate);
            if (!seed.isEmpty() && accumulated + tokens > overlapTokens) {
                break;
            }
            if (tokens <= overlapTokens || seed.isEmpty()) {
                seed.add(0, candidate);
                accumulated += tokens;
            }
            if (accumulated >= overlapTokens) {
                break;
            }
        }
        return seed;
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
