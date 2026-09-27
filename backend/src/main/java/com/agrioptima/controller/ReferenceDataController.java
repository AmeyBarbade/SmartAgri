package com.agrioptima.controller;

import com.agrioptima.dto.reference.CropResponse;
import com.agrioptima.dto.reference.FertilizerResponse;
import com.agrioptima.dto.reference.GrowthStageResponse;
import com.agrioptima.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Reference data", description = "Crops, growth stages and fertilizers (read-only)")
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    public ReferenceDataController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/crops")
    @Operation(summary = "List crops")
    public List<CropResponse> crops() {
        return referenceDataService.crops();
    }

    @GetMapping("/crops/{cropId}/stages")
    @Operation(summary = "Growth stages of a crop, in life-cycle order")
    public List<GrowthStageResponse> stages(@PathVariable Long cropId) {
        return referenceDataService.stages(cropId);
    }

    @GetMapping("/fertilizers")
    @Operation(summary = "List active fertilizers",
            description = "Grade in % N / % P2O5 / % K2O. Prices are indicative INR/kg prototype values.")
    public List<FertilizerResponse> fertilizers() {
        return referenceDataService.fertilizers();
    }
}
