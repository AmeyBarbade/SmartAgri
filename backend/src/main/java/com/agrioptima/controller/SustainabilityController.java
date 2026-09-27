package com.agrioptima.controller;

import com.agrioptima.dto.sustainability.SustainabilityResponse;
import com.agrioptima.security.UserPrincipal;
import com.agrioptima.service.SustainabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Sustainability", description = "Multi-season soil organic carbon and input optimization analytics")
public class SustainabilityController {

    private final SustainabilityService service;

    public SustainabilityController(SustainabilityService service) {
        this.service = service;
    }

    @GetMapping("/fields/{fieldId}/sustainability")
    @Operation(summary = "Get multi-season sustainability analytics and soil carbon trajectory for a field")
    public SustainabilityResponse getFieldSustainability(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable Long fieldId) {
        return service.getFieldSustainability(me.id(), fieldId);
    }
}
