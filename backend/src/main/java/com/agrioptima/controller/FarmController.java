package com.agrioptima.controller;

import com.agrioptima.dto.farm.FarmRequest;
import com.agrioptima.dto.farm.FarmResponse;
import com.agrioptima.security.UserPrincipal;
import com.agrioptima.service.FarmService;
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
@RequestMapping("/api/farms")
@Tag(name = "Farms", description = "Farms owned by the current user")
public class FarmController {

    private final FarmService farmService;

    public FarmController(FarmService farmService) {
        this.farmService = farmService;
    }

    @GetMapping
    @Operation(summary = "List my farms")
    public List<FarmResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return farmService.list(me.id());
    }

    @PostMapping
    @Operation(summary = "Create a farm")
    public ResponseEntity<FarmResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                               @Valid @RequestBody FarmRequest request) {
        FarmResponse created = farmService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/farms/" + created.id())).body(created);
    }

    @GetMapping("/{farmId}")
    @Operation(summary = "Get one of my farms", description = "404 if it does not exist or is not mine.")
    public FarmResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long farmId) {
        return farmService.get(me.id(), farmId);
    }

    @PutMapping("/{farmId}")
    @Operation(summary = "Update a farm")
    public FarmResponse update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long farmId,
                               @Valid @RequestBody FarmRequest request) {
        return farmService.update(me.id(), farmId, request);
    }

    @DeleteMapping("/{farmId}")
    @Operation(summary = "Delete a farm", description = "Also deletes its fields and their soil records.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long farmId) {
        farmService.delete(me.id(), farmId);
        return ResponseEntity.noContent().build();
    }
}
