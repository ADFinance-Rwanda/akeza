package com.tenant.management.controller;

import com.tenant.management.dto.MeResponse;
import com.tenant.management.dto.UserResponse;
import com.tenant.management.service.AccessService;
import com.tenant.management.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Identity")
public class MeController {

    private final AccessService accessService;
    private final OrganizationService organizationService;

    @GetMapping("/me")
    @Operation(summary = "Current Keycloak user and organization memberships")
    public ResponseEntity<MeResponse> me() {
        var user = accessService.currentUser();
        return ResponseEntity.ok(MeResponse.builder()
                .user(UserResponse.from(user))
                .superAdmin(user.isSuperAdmin())
                .organizations(organizationService.currentOrganizationViews())
                .build());
    }
}
