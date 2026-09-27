package com.agrioptima.dto.auth;

import com.agrioptima.entity.Role;
import com.agrioptima.entity.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String fullName, String email, Role role, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
