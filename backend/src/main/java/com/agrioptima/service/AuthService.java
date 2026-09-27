package com.agrioptima.service;

import com.agrioptima.dto.auth.AuthResponse;
import com.agrioptima.dto.auth.LoginRequest;
import com.agrioptima.dto.auth.RegisterRequest;
import com.agrioptima.dto.auth.UserResponse;
import com.agrioptima.entity.Role;
import com.agrioptima.entity.User;
import com.agrioptima.exception.ConflictException;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.UserRepository;
import com.agrioptima.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("agrioptima-timing-equalizer");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = userRepository.save(new User(
                request.fullName().trim(), email, passwordEncoder.encode(request.password()), Role.FARMER));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private AuthResponse tokenFor(User user) {
        JwtService.IssuedToken issued = jwtService.issue(user);
        return AuthResponse.bearer(issued.token(), issued.expiresAt(), UserResponse.from(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
