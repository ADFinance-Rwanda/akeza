package com.tenant.management.service;

import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Organization;
import com.tenant.management.entity.OrganizationStatus;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ForbiddenException;
import com.tenant.management.exception.InvalidCredentialsException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.OrganizationRepository;
import com.tenant.management.repository.UserRepository;
import com.tenant.management.security.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AccessService {

    public static final String ORG_HEADER = "X-Organization-Id";

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final SecurityEventLogger securityEventLogger;
    private final AuditService auditService;

    public Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidCredentialsException("Authentication is required");
        }
        return jwt;
    }

    public User currentUser() {
        String sub = currentJwt().getSubject();
        User user = userRepository.findByKeycloakSub(sub)
                .orElseThrow(() -> new ResourceNotFoundException("User is not provisioned"));
        if (!user.isActive()) {
            securityEventLogger.authorizationFailure("account_disabled");
            throw new ForbiddenException("Account is disabled");
        }
        return user;
    }

    public Long currentUserId() {
        return currentUser().getId();
    }

    public boolean isSuperAdmin() {
        return currentUser().isSuperAdmin();
    }

    public Long currentOrganizationId() {
        HttpServletRequest request = currentRequest();
        String header = request == null ? null : request.getHeader(ORG_HEADER);
        if (header == null || header.isBlank()) {
            securityEventLogger.authorizationFailure("missing_org_header");
            throw new ForbiddenException("X-Organization-Id header is required");
        }
        long organizationId;
        try {
            organizationId = Long.parseLong(header);
        } catch (NumberFormatException ex) {
            securityEventLogger.authorizationFailure("invalid_org_header");
            throw new ForbiddenException("X-Organization-Id must be a number");
        }
        requireMember(organizationId);
        return organizationId;
    }

    public void requireMember(Long organizationId) {
        requireMember(organizationId, true);
    }

    public void requireMemberAllowSuspended(Long organizationId) {
        requireMember(organizationId, false);
    }

    private void requireMember(Long organizationId, boolean enforceSuspension) {
        if (isSuperAdmin()) {
            return;
        }
        if (!membershipRepository.existsByOrganizationIdAndUserId(organizationId, currentUserId())) {
            securityEventLogger.authorizationFailure("not_org_member");
            auditService.record(organizationId, currentUserId(), "ACCESS_DENIED", "Organization", organizationId, "not_member");
            throw new ForbiddenException("You are not a member of this organization");
        }
        if (enforceSuspension) {
            rejectIfSuspended(organizationId);
        }
    }

    public void rejectIfSuspended(Long organizationId) {
        if (isSuperAdmin()) {
            return;
        }
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + organizationId));
        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            securityEventLogger.authorizationFailure("org_suspended");
            throw new ForbiddenException("Organization is suspended");
        }
    }

    public MemberRole currentRole(Long organizationId) {
        if (isSuperAdmin()) {
            return MemberRole.ORG_ADMIN;
        }
        return membershipRepository.findByOrganizationIdAndUserId(organizationId, currentUserId())
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"))
                .getRole();
    }

    public void requireRole(Long organizationId, MemberRole... roles) {
        requireRole(organizationId, true, roles);
    }

    public void requireRoleAllowSuspended(Long organizationId, MemberRole... roles) {
        requireRole(organizationId, false, roles);
    }

    private void requireRole(Long organizationId, boolean enforceSuspension, MemberRole... roles) {
        if (isSuperAdmin()) {
            return;
        }
        requireMember(organizationId, enforceSuspension);
        MemberRole current = currentRole(organizationId);
        if (Arrays.stream(roles).noneMatch(role -> role == current)) {
            securityEventLogger.authorizationFailure("insufficient_role");
            auditService.record(organizationId, currentUserId(), "ACCESS_DENIED", "Organization", organizationId, "role=" + current);
            throw new ForbiddenException("You do not have permission to perform this action");
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
