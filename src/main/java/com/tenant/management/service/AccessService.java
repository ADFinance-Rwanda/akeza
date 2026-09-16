package com.tenant.management.service;

import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import com.tenant.management.exception.ForbiddenException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final MembershipRepository membershipRepository;

    public UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ForbiddenException("Authentication is required");
        }
        return principal;
    }

    public Long currentUserId() {
        return currentPrincipal().getId();
    }

    public void requireSelf(Long userId) {
        if (!currentUserId().equals(userId)) {
            throw new ForbiddenException("You can only access your own user account");
        }
    }

    public void requireMember(Long tenantId) {
        if (!membershipRepository.existsByTenantIdAndUserId(tenantId, currentUserId())) {
            throw new ForbiddenException("You are not a member of this tenant");
        }
    }

    public void requireRole(Long tenantId, MemberRole... roles) {
        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, currentUserId())
                .orElseThrow(() -> new ForbiddenException("You are not a member of this tenant"));
        List<MemberRole> allowed = Arrays.asList(roles);
        if (!allowed.contains(membership.getRole())) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
    }

    public void requireSelfOrSharedTenant(Long userId) {
        if (currentUserId().equals(userId)) {
            return;
        }
        if (!membershipRepository.existsSharedTenant(currentUserId(), userId)) {
            throw new ForbiddenException("You do not have permission to access this user");
        }
    }
}
