package com.agrioptima.dto.recommendation;

import java.time.LocalDateTime;

/** One stored recommendation in a field's history. */
public record RecommendationSummary(Long id, LocalDateTime createdAt, String status, boolean feasible,
                                    String cropCode, String stageCode, String selectedStrategy, String scoringMode,
                                    String modelVersion) {
}
