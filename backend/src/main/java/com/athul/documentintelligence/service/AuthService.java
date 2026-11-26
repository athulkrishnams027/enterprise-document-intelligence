package com.athul.documentintelligence.service;

import com.athul.documentintelligence.dto.request.LoginRequest;
import com.athul.documentintelligence.dto.request.RegisterRequest;
import com.athul.documentintelligence.dto.response.AuthResponse;
import com.athul.documentintelligence.dto.response.UserProfileResponse;
import com.athul.documentintelligence.entity.Role;
import com.athul.documentintelligence.entity.User;
import com.athul.documentintelligence.enums.RoleName;
import com.athul.documentintelligence.exception.DuplicateResourceException;
import com.athul.documentintelligence.exception.ResourceNotFoundException;
import com.athul.documentintelligence.mapper.UserMapper;
import com.athul.documentintelligence.repository.RoleRepository;
import com.athul.documentintelligence.repository.UserRepository;
import com.athul.documentintelligence.security.AuthenticatedUser;
import com.athul.documentintelligence.security.JwtService;
import com.athul.documentintelligence.security.TokenRevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Role userRole = roleRepository.findByName(RoleName.USER.name())
                .orElseThrow(() -> new IllegalStateException("Default USER role is missing from database"));

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);
        log.info("Registered new user id={} role={}", saved.getId(), RoleName.USER.name());

        AuthenticatedUser principal = new AuthenticatedUser(
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getPasswordHash(),
                saved.isEnabled(),
                Set.of(RoleName.USER.name()),
                Set.of(new SimpleGrantedAuthority("ROLE_" + RoleName.USER.name()))
        );

        String token = jwtService.generateToken(principal);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), userMapper.toProfileResponse(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = userRepository.findWithRolesById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));

        String token = jwtService.generateToken(principal);
        log.info("User logged in userId={}", user.getId());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), userMapper.toProfileResponse(user));
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return;
        }
        try {
            Claims claims = jwtService.parseClaims(token);
            tokenRevocationService.revoke(jwtService.extractJti(claims), jwtService.extractExpiration(claims));
            log.info("Revoked JWT session for userEmail={}", jwtService.extractEmail(claims));
        } catch (JwtException ignored) {
            // Invalid or already expired tokens need no revocation record
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
        return userMapper.toProfileResponse(user);
    }
}
