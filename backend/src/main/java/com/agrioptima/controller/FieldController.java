package com.agrioptima.controller;

import com.agrioptima.dto.field.FieldRequest;
import com.agrioptima.dto.field.FieldResponse;
import com.agrioptima.security.UserPrincipal;
import com.agrioptima.service.FieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Fields", description = "Fields within my farms")
public class FieldController {

    private final FieldService fieldService;

    public FieldController(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    @GetMapping("/farms/{farmId}/fields")
    @Operation(summary = "List fields of one of my farms")
    public List<FieldResponse> list(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long farmId) {
        return fieldService.listForFarm(me.id(), farmId);
    }

    @PostMapping("/farms/{farmId}/fields")
    @Operation(summary = "Create a field in one of my farms",
            description = "growthStageId must belong to cropId (400 otherwise).")
    public ResponseEntity<FieldResponse> create(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long farmId,
                                                @Valid @RequestBody FieldRequest request) {
        FieldResponse created = fieldService.create(me.id(), farmId, request);
        return ResponseEntity.created(URI.create("/api/fields/" + created.id())).body(created);
    }

    @GetMapping("/fields/{fieldId}")
    @Operation(summary = "Get one of my fields")
    public FieldResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId) {
        return fieldService.get(me.id(), fieldId);
    }

    @PutMapping("/fields/{fieldId}")
    @Operation(summary = "Update a field (crop, growth stage, season, ...)")
    public FieldResponse update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId,
                                @Valid @RequestBody FieldRequest request) {
        return fieldService.update(me.id(), fieldId, request);
    }

    @DeleteMapping("/fields/{fieldId}")
    @Operation(summary = "Delete a field", description = "Also deletes its soil records.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long fieldId) {
        fieldService.delete(me.id(), fieldId);
        return ResponseEntity.noContent().build();
    }
}
