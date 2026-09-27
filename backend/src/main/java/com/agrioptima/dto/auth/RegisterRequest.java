package com.agrioptima.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100)
        String fullName,

        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt only uses the first 72 bytes, so longer passwords are rejected rather than silently truncated.
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password
) {
}
