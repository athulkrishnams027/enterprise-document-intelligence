package com.athul.documentintelligence.controller;

import com.athul.documentintelligence.dto.response.UserDashboardResponse;
import com.athul.documentintelligence.security.SecurityUtils;
import com.athul.documentintelligence.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<UserDashboardResponse> summary() {
        return ResponseEntity.ok(dashboardService.getUserDashboard(SecurityUtils.requireCurrentUserId()));
    }
}
