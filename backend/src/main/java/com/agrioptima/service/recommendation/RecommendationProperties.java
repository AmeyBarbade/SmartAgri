package com.agrioptima.service.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Prototype parameters of the recommendation run (docs/RECOMMENDATION_FLOW.md). None of them is an agronomic
 * constant; all are reported in every response.
 *
 * @param cropPriceInrPerTonne   grain price used for expected revenue, INR per tonne, by crop code
 * @param excessPenaltyInrPerKg  penalty per kg/ha of N + P2O5 + K2O supplied beyond the requirement
 * @param maxKgHaPerProduct      per-product cap sent to the optimizer and re-checked by the verifier
 */
@Validated
@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(@NotNull Map<String, BigDecimal> cropPriceInrPerTonne,
                                       @DecimalMin("0") double excessPenaltyInrPerKg,
                                       @Positive double maxKgHaPerProduct,
                                       @NotNull String cropPriceSource) {
}
