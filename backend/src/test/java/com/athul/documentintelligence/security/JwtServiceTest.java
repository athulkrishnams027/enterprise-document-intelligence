package com.athul.documentintelligence.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 3600_000L)
    );

    @Test
    void shouldGenerateAndParseValidToken() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                "alice@example.com",
                "Alice Doe",
                "encoded",
                true,
                Set.of("USER"),
                Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = jwtService.generateToken(principal);
        Claims claims = jwtService.parseClaims(token);

        assertThat(jwtService.extractEmail(claims)).isEqualTo("alice@example.com");
        assertThat(claims.get("uid", String.class)).isEqualTo(userId.toString());
        assertThat(jwtService.extractJti(claims)).isNotBlank();
    }

    @Test
    void shouldRejectTamperedToken() {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(),
                "bob@example.com",
                "Bob",
                "encoded",
                true,
                Set.of("USER"),
                Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = jwtService.generateToken(principal) + "tamper";
        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }
}
