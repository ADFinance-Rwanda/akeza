package com.tenant.management.service;

import com.tenant.management.dto.CreateMembershipRequest;
import com.tenant.management.dto.MembershipResponse;
import com.tenant.management.dto.UpdateMembershipRequest;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import com.tenant.management.entity.Tenant;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final TenantService tenantService;
    private final UserService userService;
    private final AccessService accessService;

    @Transactional(readOnly = true)
    public List<MembershipResponse> getMembersByTenant(Long tenantId) {
        tenantService.getTenant(tenantId);
        accessService.requireMember(tenantId);
        return membershipRepository.findAllByTenantId(tenantId).stream()
                .map(MembershipResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getTenantsByUser(Long userId) {
        userService.getUser(userId);
        accessService.requireSelf(userId);
        return membershipRepository.findAllByUserId(userId).stream()
                .map(MembershipResponse::fromEntity)
                .toList();
    }

    @Transactional
    public MembershipResponse addMember(Long tenantId, CreateMembershipRequest request) {
        Tenant tenant = tenantService.getTenant(tenantId);
        accessService.requireRole(tenantId, MemberRole.OWNER, MemberRole.ADMIN);
        rejectAdminAssigningOwner(tenantId, request.getRole());
        User user = userService.getUser(request.getUserId());

        if (membershipRepository.existsByTenantIdAndUserId(tenantId, request.getUserId())) {
            throw new ConflictException("User is already a member of this tenant");
        }

        Membership membership = Membership.builder()
                .tenant(tenant)
                .user(user)
                .role(request.getRole())
                .build();

        return MembershipResponse.fromEntity(membershipRepository.save(membership));
    }

    @Transactional
    public MembershipResponse updateMemberRole(Long tenantId, Long userId, UpdateMembershipRequest request) {
        Membership membership = getMembership(tenantId, userId);
        requireCanManageMember(tenantId, membership);
        rejectAdminAssigningOwner(tenantId, request.getRole());

        if (membership.getRole() == MemberRole.OWNER
                && request.getRole() != MemberRole.OWNER
                && membershipRepository.countByTenantIdAndRole(tenantId, MemberRole.OWNER) <= 1) {
            throw new ConflictException("Cannot change role of the last owner in a tenant");
        }

        membership.setRole(request.getRole());
        return MembershipResponse.fromEntity(membershipRepository.save(membership));
    }

    @Transactional
    public void removeMember(Long tenantId, Long userId) {
        Membership membership = getMembership(tenantId, userId);
        requireCanManageMember(tenantId, membership);

        if (membership.getRole() == MemberRole.OWNER
                && membershipRepository.countByTenantIdAndRole(tenantId, MemberRole.OWNER) <= 1) {
            throw new ConflictException("Cannot remove the last owner from a tenant");
        }

        membershipRepository.delete(membership);
    }

    private Membership getMembership(Long tenantId, Long userId) {
        tenantService.getTenant(tenantId);
        userService.getUser(userId);
        return membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership not found for tenant id " + tenantId + " and user id " + userId));
    }

    private void requireCanManageMember(Long tenantId, Membership target) {
        accessService.requireRole(tenantId, MemberRole.OWNER, MemberRole.ADMIN);
        if (target.getRole() == MemberRole.OWNER) {
            accessService.requireRole(tenantId, MemberRole.OWNER);
        }
    }

    private void rejectAdminAssigningOwner(Long tenantId, MemberRole role) {
        if (role == MemberRole.OWNER) {
            accessService.requireRole(tenantId, MemberRole.OWNER);
        }
    }
}
