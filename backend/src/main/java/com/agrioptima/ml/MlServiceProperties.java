package com.agrioptima.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

/**
 * Connection to the FastAPI ML service (Milestone 6). {@code baseUrl} comes from {@code ML_SERVICE_BASE_URL}
 * (default {@code http://localhost:8001}). It is never included in API responses.
 */
@Validated
@ConfigurationProperties(prefix = "app.ml")
public record MlServiceProperties(@NotBlank String baseUrl, @NotNull Duration connectTimeout,
                                  @NotNull Duration readTimeout) {
}
