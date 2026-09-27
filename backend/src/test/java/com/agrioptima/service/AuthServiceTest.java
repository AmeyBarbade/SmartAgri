package com.agrioptima.service;

import com.agrioptima.dto.auth.AuthResponse;
import com.agrioptima.dto.auth.LoginRequest;
import com.agrioptima.dto.auth.RegisterRequest;
import com.agrioptima.entity.Role;
import com.agrioptima.entity.User;
import com.agrioptima.exception.ConflictException;
import com.agrioptima.repository.UserRepository;
import com.agrioptima.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    JwtService jwtService;

    // Real encoder: the point is to verify real hashing behaviour, not a mock's.
    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerHashesPasswordAndNormalisesEmail() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issue(any())).thenReturn(new JwtService.IssuedToken("jwt", Instant.EPOCH));

        AuthResponse response = authService.register(
                new RegisterRequest("  Asha Patel ", "  Asha@Example.COM ", "wheat2026"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("asha@example.com");
        assertThat(user.getFullName()).isEqualTo("Asha Patel");
        assertThat(user.getRole()).isEqualTo(Role.FARMER);
        assertThat(user.getPasswordHash()).isNotEqualTo("wheat2026").startsWith("$2");
        assertThat(passwordEncoder.matches("wheat2026", user.getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void registerRejectsDuplicateEmailWithoutSaving() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("X Y", "Taken@example.com", "secret123")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithCorrectPasswordIssuesToken() {
        User user = new User("A", "a@example.com", passwordEncoder.encode("secret123"), Role.FARMER);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(jwtService.issue(user)).thenReturn(new JwtService.IssuedToken("jwt", Instant.EPOCH));

        assertThat(authService.login(new LoginRequest("A@example.com", "secret123")).accessToken()).isEqualTo("jwt");
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailFailsIdentically() {
        User user = new User("A", "a@example.com", passwordEncoder.encode("secret123"), Role.FARMER);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        verify(jwtService, never()).issue(any());
    }
}
