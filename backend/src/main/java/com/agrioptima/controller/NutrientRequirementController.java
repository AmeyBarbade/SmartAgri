package com.agrioptima.controller;

import com.agrioptima.dto.requirement.NutrientRequirementResponse;
import com.agrioptima.engine.knowledge.KnowledgeBase;
import com.agrioptima.security.UserPrincipal;
import com.agrioptima.service.NutrientRequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Nutrient requirement", description = "Deterministic N / P2O5 / K2O requirement from the knowledge base")
public class NutrientRequirementController {

    private final NutrientRequirementService service;

    public NutrientRequirementController(NutrientRequirementService service) {
        this.service = service;
    }

    @GetMapping("/fields/{fieldId}/nutrient-requirement")
    @Operation(summary = "Nutrient requirement for a field at its current growth stage",
            description = "Uses the field's crop, growth stage, area, irrigation type, sowing date, latest soil test "
                    + "and recorded fertilizer applications. Not persisted. No ML, no optimisation.")
    public NutrientRequirementResponse forField(
            @AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId,
            @Parameter(description = "Optional recommendation profile code (see availableProfiles in the response)")
            @RequestParam(required = false) @Pattern(regexp = "[A-Z0-9_]{1,60}") String profile) {
        return service.forField(me.id(), fieldId, profile);
    }

    @GetMapping("/knowledge-base")
    @Operation(summary = "The nutrient knowledge base in use (values, statuses, sources)")
    public KnowledgeBase knowledgeBase() {
        return service.knowledgeBase();
    }
}
