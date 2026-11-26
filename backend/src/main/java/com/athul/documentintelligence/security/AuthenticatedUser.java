package com.athul.documentintelligence.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticated principal derived from verified database user state and JWT authentication.
 */
@Getter
public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String fullName;
    private final String password;
    private final boolean enabled;
    private final Set<String> roleNames;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUser(
            UUID id,
            String email,
            String fullName,
            String password,
            boolean enabled,
            Set<String> roleNames,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.password = password;
        this.enabled = enabled;
        this.roleNames = roleNames;
        this.authorities = authorities;
    }

    public boolean isAdmin() {
        return roleNames.contains("ADMIN");
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
