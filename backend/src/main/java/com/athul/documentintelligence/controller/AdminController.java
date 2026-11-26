package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.dto.response.AdminDashboardResponse;
import com.athul.documentintelligence.dto.response.AiAuditLogResponse;
import com.athul.documentintelligence.dto.response.PagedResponse;
import com.athul.documentintelligence.dto.response.UserProfileResponse;
import com.athul.documentintelligence.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> dashboard() {
        return ResponseEntity.ok(adminService.getSystemDashboard());
    }

    @GetMapping("/users")
    public ResponseEntity<PagedResponse<UserProfileResponse>> users(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(adminService.listUsers(pageable));
    }

    @GetMapping("/ai-usage")
    public ResponseEntity<PagedResponse<AiAuditLogResponse>> aiUsage(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(adminService.listAiUsage(pageable));
    }
}
