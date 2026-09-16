package com.tenant.management.controller;

import com.tenant.management.dto.CreateMembershipRequest;
import com.tenant.management.dto.CreateTenantRequest;
import com.tenant.management.dto.MembershipResponse;
import com.tenant.management.dto.TenantResponse;
import com.tenant.management.dto.UpdateMembershipRequest;
import com.tenant.management.dto.UpdateTenantRequest;
import com.tenant.management.service.MembershipService;
import com.tenant.management.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Tenant CRUD and membership management")
public class TenantController {

    private final TenantService tenantService;
    private final MembershipService membershipService;

    @GetMapping
    @Operation(summary = "List tenants for the current user")
    public ResponseEntity<List<TenantResponse>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tenant by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Not a member of this tenant"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<TenantResponse> getTenantById(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantById(id));
    }

    @PostMapping
    @Operation(summary = "Create a tenant and become its owner")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "409", description = "Slug already exists")
    })
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a tenant (owner only)")
    public ResponseEntity<TenantResponse> updateTenant(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tenant (owner only)")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List members of a tenant")
    public ResponseEntity<List<MembershipResponse>> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(membershipService.getMembersByTenant(id));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a member to a tenant (owner or admin)")
    public ResponseEntity<MembershipResponse> addMember(@PathVariable Long id,
                                                        @Valid @RequestBody CreateMembershipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.addMember(id, request));
    }

    @PutMapping("/{id}/members/{userId}")
    @Operation(summary = "Change a member role (owner or admin)")
    public ResponseEntity<MembershipResponse> updateMemberRole(@PathVariable Long id,
                                                               @PathVariable Long userId,
                                                               @Valid @RequestBody UpdateMembershipRequest request) {
        return ResponseEntity.ok(membershipService.updateMemberRole(id, userId, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a member (owner or admin)")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        membershipService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
