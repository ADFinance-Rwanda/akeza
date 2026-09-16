package com.tenant.management.service;

import com.tenant.management.dto.CreateTenantRequest;
import com.tenant.management.dto.TenantResponse;
import com.tenant.management.dto.UpdateTenantRequest;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import com.tenant.management.entity.Tenant;
import com.tenant.management.entity.TenantStatus;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final UserService userService;
    private final AccessService accessService;

    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants() {
        return membershipRepository.findAllByUserId(accessService.currentUserId()).stream()
                .map(membership -> TenantResponse.fromEntity(membership.getTenant()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenantById(Long id) {
        Tenant tenant = getTenant(id);
        accessService.requireMember(id);
        return TenantResponse.fromEntity(tenant);
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        String slug = normalizeSlug(request.getSlug());
        if (tenantRepository.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("Tenant with slug '" + slug + "' already exists");
        }

        Tenant tenant = Tenant.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(trimToNull(request.getDescription()))
                .status(TenantStatus.ACTIVE)
                .build();
        Tenant saved = tenantRepository.save(tenant);

        User owner = userService.getUser(accessService.currentUserId());
        membershipRepository.save(Membership.builder()
                .tenant(saved)
                .user(owner)
                .role(MemberRole.OWNER)
                .build());

        return TenantResponse.fromEntity(saved);
    }

    @Transactional
    public TenantResponse updateTenant(Long id, UpdateTenantRequest request) {
        Tenant tenant = getTenant(id);
        accessService.requireRole(id, MemberRole.OWNER);

        if (request.getName() != null) {
            tenant.setName(requireText(request.getName(), "Tenant name cannot be blank"));
        }
        if (request.getSlug() != null) {
            String slug = normalizeSlug(request.getSlug());
            if (slug.isEmpty()) {
                throw new IllegalArgumentException("Tenant slug cannot be blank");
            }
            if (!slug.equalsIgnoreCase(tenant.getSlug()) && tenantRepository.existsBySlugIgnoreCase(slug)) {
                throw new ConflictException("Tenant with slug '" + slug + "' already exists");
            }
            tenant.setSlug(slug);
        }
        if (request.getDescription() != null) {
            tenant.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getStatus() != null) {
            tenant.setStatus(request.getStatus());
        }

        return TenantResponse.fromEntity(tenantRepository.save(tenant));
    }

    @Transactional
    public void deleteTenant(Long id) {
        Tenant tenant = getTenant(id);
        accessService.requireRole(id, MemberRole.OWNER);
        tenantRepository.delete(tenant);
    }

    public Tenant getTenant(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + id));
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }

    private String requireText(String value, String message) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
