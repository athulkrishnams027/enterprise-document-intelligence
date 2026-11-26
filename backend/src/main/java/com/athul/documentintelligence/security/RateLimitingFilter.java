package com.athul.documentintelligence.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight fixed-window per-principal/IP rate limiter for API endpoints.
 * Designed to be swappable with a distributed Redis/Bucket4j implementation behind an API gateway.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int requestsPerMinute;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.requests-per-minute:60}") int requestsPerMinute
    ) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !enabled || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = resolveClientKey(request);
        long currentMinute = Instant.now().getEpochSecond() / 60;

        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || existing.minuteEpoch != currentMinute) {
                return new WindowCounter(currentMinute);
            }
            return existing;
        });

        int count = counter.count.incrementAndGet();
        if (count > requestsPerMinute) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"timestamp\":\"" + Instant.now() + "\","
                            + "\"status\":429,"
                            + "\"error\":\"Too Many Requests\","
                            + "\"code\":\"RATE_LIMIT_EXCEEDED\","
                            + "\"message\":\"Rate limit exceeded. Please try again shortly.\","
                            + "\"path\":\"" + escapeJson(request.getRequestURI()) + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.getId();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private String escapeJson(String input) {
        return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class WindowCounter {
        final long minuteEpoch;
        final AtomicInteger count = new AtomicInteger(0);

        WindowCounter(long minuteEpoch) {
            this.minuteEpoch = minuteEpoch;
        }
    }
}
