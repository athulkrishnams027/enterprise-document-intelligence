package com.athul.documentintelligence.service;

import com.athul.documentintelligence.dto.request.RegisterRequest;
import com.athul.documentintelligence.dto.response.AuthResponse;
import com.athul.documentintelligence.entity.Role;
import com.athul.documentintelligence.entity.User;
import com.athul.documentintelligence.exception.DuplicateResourceException;
import com.athul.documentintelligence.mapper.UserMapper;
import com.athul.documentintelligence.repository.RoleRepository;
import com.athul.documentintelligence.repository.UserRepository;
import com.athul.documentintelligence.security.JwtProperties;
import com.athul.documentintelligence.security.JwtService;
import com.athul.documentintelligence.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtService jwtService = new JwtService(
                new JwtProperties("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 3600_000L)
        );
        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                new TokenRevocationService(),
                new UserMapper()
        );
    }

    @Test
    void registerCreatesUserWithEncodedPasswordAndUserRole() {
        Role userRole = new Role();
        userRole.setId(UUID.randomUUID());
        userRole.setName("USER");

        when(userRepository.existsByEmailIgnoreCase("jane@enterprise.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse response = authService.register(
                new RegisterRequest("Jane Doe", "Jane@Enterprise.com", "StrongPass123!")
        );

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("jane@enterprise.com");
        assertThat(response.user().roles()).containsExactly("USER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("dup@enterprise.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Dup User", "dup@enterprise.com", "StrongPass123!")
        )).isInstanceOf(DuplicateResourceException.class);
    }
}
