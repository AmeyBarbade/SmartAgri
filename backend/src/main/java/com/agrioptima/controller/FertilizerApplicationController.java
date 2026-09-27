package com.agrioptima.controller;

import com.agrioptima.dto.application.FertilizerApplicationRequest;
import com.agrioptima.dto.application.FertilizerApplicationResponse;
import com.agrioptima.security.UserPrincipal;
import com.agrioptima.service.FertilizerApplicationService;
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
@Tag(name = "Fertilizer applications", description = "Fertilizer already applied to my fields (previous usage)")
public class FertilizerApplicationController {

    private final FertilizerApplicationService service;

    public FertilizerApplicationController(FertilizerApplicationService service) {
        this.service = service;
    }

    @GetMapping("/fields/{fieldId}/applications")
    @Operation(summary = "Previous fertilizer applications for a field, newest first")
    public List<FertilizerApplicationResponse> list(@AuthenticationPrincipal UserPrincipal me,
                                                    @PathVariable Long fieldId) {
        return service.list(me.id(), fieldId);
    }

    @PostMapping("/fields/{fieldId}/applications")
    @Operation(summary = "Record a fertilizer application",
            description = "quantityKg is kg of product applied to the whole field.")
    public ResponseEntity<FertilizerApplicationResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                                @PathVariable Long fieldId,
                                                                @Valid @RequestBody FertilizerApplicationRequest request) {
        FertilizerApplicationResponse created = service.create(me.id(), fieldId, request);
        return ResponseEntity.created(URI.create("/api/applications/" + created.id())).body(created);
    }

    @DeleteMapping("/applications/{applicationId}")
    @Operation(summary = "Delete a fertilizer application")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long applicationId) {
        service.delete(me.id(), applicationId);
        return ResponseEntity.noContent().build();
    }
}
