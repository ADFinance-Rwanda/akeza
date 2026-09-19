package com.tenant.management.controller;

import com.tenant.management.dto.AddMemberRequest;
import com.tenant.management.dto.AuditLogResponse;
import com.tenant.management.dto.CreateOrganizationRequest;
import com.tenant.management.dto.JobResponse;
import com.tenant.management.dto.MembershipResponse;
import com.tenant.management.dto.OrganizationResponse;
import com.tenant.management.dto.PageResponse;
import com.tenant.management.dto.UpdateMemberRoleRequest;
import com.tenant.management.dto.UpdateOrganizationStatusRequest;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.repository.AuditLogRepository;
import com.tenant.management.service.AccessService;
import com.tenant.management.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final AuditLogRepository auditLogRepository;
    private final AccessService accessService;

    @GetMapping
    @Operation(summary = "List organizations for the current user")
    public ResponseEntity<List<OrganizationResponse>> list() {
        return ResponseEntity.ok(organizationService.listMine());
    }

    @PostMapping
    @Operation(summary = "Create an organization and become ORG_ADMIN")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an organization")
    public ResponseEntity<OrganizationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.get(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an organization (ORG_ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List members")
    public ResponseEntity<List<MembershipResponse>> members(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.members(id));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a member by email (ORG_ADMIN)")
    public ResponseEntity<MembershipResponse> addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.addMember(id, request.getEmail(), request.getRole()));
    }

    @PutMapping("/{id}/members/{userId}")
    @Operation(summary = "Change a member role (ORG_ADMIN)")
    public ResponseEntity<MembershipResponse> updateMemberRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return ResponseEntity.ok(organizationService.updateMemberRole(id, userId, request.getRole()));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Suspend or reactivate an organization (ORG_ADMIN or SUPER_ADMIN)")
    public ResponseEntity<OrganizationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationStatusRequest request
    ) {
        return ResponseEntity.ok(organizationService.updateStatus(id, request.getStatus()));
    }

    @GetMapping("/{id}/jobs/dead-letters")
    @Operation(summary = "Inspect failed jobs for the organization (ORG_ADMIN)")
    public ResponseEntity<List<JobResponse>> deadLetters(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.deadLetters(id));
    }

    @GetMapping("/{id}/audit-logs")
    @Operation(summary = "Read-only audit log for the organization (ORG_ADMIN)")
    public ResponseEntity<PageResponse<AuditLogResponse>> auditLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) LocalDate date
    ) {
        accessService.requireRole(id, MemberRole.ORG_ADMIN);
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
        }
        String query = sanitize(q);
        String actionFilter = action == null ? "" : action.trim();
        String entityFilter = entityType == null ? "" : entityType.trim();
        LocalDateTime from = date == null ? null : date.atStartOfDay();
        LocalDateTime to = date == null ? null : date.plusDays(1).atStartOfDay();
        return ResponseEntity.ok(PageResponse.from(
                auditLogRepository.search(
                                id,
                                actionFilter,
                                userId,
                                entityFilter,
                                from,
                                to,
                                query,
                                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(AuditLogResponse::from)));
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 80) {
            trimmed = trimmed.substring(0, 80);
        }
        return trimmed.replace("%", "");
    }
}
