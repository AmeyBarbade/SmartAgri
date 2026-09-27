package com.agrioptima.controller;

import com.agrioptima.ml.MlContracts.SoilGridsResponse;
import com.agrioptima.ml.MlServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/soilgrids")
@Tag(name = "SoilGrids", description = "Satellite soil properties proxy (ISRIC SoilGrids v2.0)")
public class SoilGridsController {

    private final MlServiceClient ml;

    public SoilGridsController(MlServiceClient ml) {
        this.ml = ml;
    }

    @GetMapping
    @Operation(summary = "Fetch satellite-derived soil data (0-5cm layer) from ISRIC SoilGrids v2.0")
    public ResponseEntity<SoilGridsResponse> getSoilGrids(@RequestParam double lat, @RequestParam double lon) {
        SoilGridsResponse resp = ml.fetchSoilGrids(lat, lon);
        if (resp == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resp);
    }
}
