package com.agrioptima.controller;

import com.agrioptima.dto.soil.SoilRecordRequest;
import com.agrioptima.dto.soil.SoilRecordResponse;
import com.agrioptima.security.UserPrincipal;
import com.agrioptima.service.SoilRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Soil records", description = "Soil tests for my fields (N/P/K in kg/ha available)")
public class SoilRecordController {

    private final SoilRecordService soilRecordService;

    public SoilRecordController(SoilRecordService soilRecordService) {
        this.soilRecordService = soilRecordService;
    }

    @GetMapping("/fields/{fieldId}/soil-records")
    @Operation(summary = "Soil test history for a field, newest first")
    public List<SoilRecordResponse> history(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId) {
        return soilRecordService.history(me.id(), fieldId);
    }

    @GetMapping("/fields/{fieldId}/soil-records/latest")
    @Operation(summary = "Most recent soil test for a field", description = "404 if the field has no soil records.")
    public SoilRecordResponse latest(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId) {
        return soilRecordService.latest(me.id(), fieldId);
    }

    @PostMapping("/fields/{fieldId}/soil-records")
    @Operation(summary = "Add a soil test to a field")
    public ResponseEntity<SoilRecordResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                     @PathVariable Long fieldId,
                                                     @Valid @RequestBody SoilRecordRequest request) {
        SoilRecordResponse created = soilRecordService.create(me.id(), fieldId, request);
        return ResponseEntity.created(URI.create("/api/soil-records/" + created.id())).body(created);
    }

    @GetMapping("/soil-records/{recordId}")
    @Operation(summary = "Get one soil test")
    public SoilRecordResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long recordId) {
        return soilRecordService.get(me.id(), recordId);
    }

    @DeleteMapping("/soil-records/{recordId}")
    @Operation(summary = "Delete a soil test")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long recordId) {
        soilRecordService.delete(me.id(), recordId);
        return ResponseEntity.noContent().build();
    }
}
