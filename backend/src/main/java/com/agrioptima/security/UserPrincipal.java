package com.agrioptima.security;

import com.agrioptima.entity.Role;
import com.agrioptima.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Authenticated caller, resolved from a valid JWT and stored in the SecurityContext. */
public record UserPrincipal(Long id, String email, String fullName, Role role) {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
