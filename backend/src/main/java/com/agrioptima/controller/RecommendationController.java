package com.agrioptima.controller;

import com.agrioptima.dto.recommendation.RecommendationResponse;
import com.agrioptima.dto.recommendation.RecommendationSummary;
import com.agrioptima.security.UserPrincipal;
import com.agrioptima.service.recommendation.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Recommendations", description = "End-to-end fertilizer recommendation: requirement -> optimizer -> "
        + "yield prediction -> scoring")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @PostMapping("/fields/{fieldId}/recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run and store a recommendation for a field",
            description = "Nutrient requirement (knowledge base) -> three optimizer plans (re-verified) -> predicted "
                    + "yield per plan -> transparent score -> selected plan. An unreachable requirement is returned "
                    + "with feasible=false (201). 502/503/504 if the ML service fails.")
    public RecommendationResponse create(
            @AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId,
            @Parameter(description = "Optional requirement profile code (as in /nutrient-requirement)")
            @RequestParam(required = false) @Pattern(regexp = "[A-Z0-9_]{1,60}") String profile) {
        return service.create(me.id(), fieldId, profile);
    }

    @GetMapping("/fields/{fieldId}/recommendations")
    @Operation(summary = "Stored recommendations of a field, newest first")
    public List<RecommendationSummary> history(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId) {
        return service.history(me.id(), fieldId);
    }

    @GetMapping("/recommendations/{recommendationId}")
    @Operation(summary = "A stored recommendation, exactly as it was computed")
    public RecommendationResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long recommendationId) {
        return service.get(me.id(), recommendationId);
    }
}
