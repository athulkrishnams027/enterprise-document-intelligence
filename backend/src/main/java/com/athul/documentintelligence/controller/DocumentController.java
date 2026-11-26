package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.dto.response.BatchUploadResponse;
import com.athul.documentintelligence.dto.response.DocumentDetailResponse;
import com.athul.documentintelligence.dto.response.DocumentResponse;
import com.athul.documentintelligence.dto.response.DocumentStatusResponse;
import com.athul.documentintelligence.dto.response.PagedResponse;
import com.athul.documentintelligence.enums.DocumentStatus;
import com.athul.documentintelligence.security.SecurityUtils;
import com.athul.documentintelligence.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        UUID ownerId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.uploadDocument(ownerId, file));
    }

    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchUploadResponse> uploadMultiple(@RequestParam("files") List<MultipartFile> files) {
        UUID ownerId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.uploadMultiple(ownerId, files));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<DocumentResponse>> list(
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID ownerId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(documentService.listUserDocuments(ownerId, status, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailResponse> getDetails(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(documentService.getDocumentDetails(id, ownerId));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DocumentStatusResponse> getStatus(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.ok(documentService.getDocumentStatus(id, ownerId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireCurrentUserId();
        documentService.deleteDocument(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentStatusResponse> reprocess(@PathVariable UUID id) {
        UUID ownerId = SecurityUtils.requireCurrentUserId();
        return ResponseEntity.accepted().body(documentService.reprocessDocument(id, ownerId));
    }
}
