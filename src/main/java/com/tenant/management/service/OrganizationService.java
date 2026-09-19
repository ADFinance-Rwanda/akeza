package com.tenant.management.service;

import com.tenant.management.dto.CreateOrganizationRequest;
import com.tenant.management.dto.JobResponse;
import com.tenant.management.dto.MembershipResponse;
import com.tenant.management.dto.OrganizationResponse;
import com.tenant.management.entity.JobStatus;
import com.tenant.management.repository.JobRepository;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import com.tenant.management.entity.Organization;
import com.tenant.management.entity.OrganizationStatus;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ForbiddenException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.OrganizationRepository;
import com.tenant.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final AccessService accessService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listMine() {
        if (accessService.isSuperAdmin()) {
            return organizationRepository.findAll().stream()
                    .map(OrganizationResponse::from)
                    .toList();
        }
        return membershipRepository.findAllByUserId(accessService.currentUserId()).stream()
                .map(membership -> OrganizationResponse.from(membership.getOrganization()))
                    .toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> currentOrganizationViews() {
        User user = accessService.currentUser();
        if (accessService.isSuperAdmin()) {
            return organizationRepository.findAll().stream()
                    .map(organization -> MembershipResponse.builder()
                            .organizationId(organization.getId())
                            .organizationName(organization.getName())
                            .userId(user.getId())
                            .userEmail(user.getEmail())
                            .role(MemberRole.ORG_ADMIN)
                            .build())
                    .toList();
        }
        return membershipRepository.findAllByUserId(user.getId()).stream()
                .map(MembershipResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(Long id) {
        accessService.requireMember(id);
        return OrganizationResponse.from(getOrg(id));
    }

    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        String slug = request.getSlug().trim().toLowerCase();
        if (organizationRepository.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("Organization slug already exists");
        }
        Organization organization = organizationRepository.save(Organization.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(trimToNull(request.getDescription()))
                .status(OrganizationStatus.ACTIVE)
                .build());
        User owner = accessService.currentUser();
        membershipRepository.save(Membership.builder()
                .organization(organization)
                .user(owner)
                .role(MemberRole.ORG_ADMIN)
                .build());
        auditService.record(organization.getId(), owner.getId(), "ORGANIZATION_CREATED", "Organization", organization.getId(), slug);
        return OrganizationResponse.from(organization);
    }

    @Transactional
    public void delete(Long id) {
        accessService.requireRole(id, MemberRole.ORG_ADMIN);
        Organization organization = getOrg(id);
        organizationRepository.delete(organization);
        auditService.record(id, accessService.currentUserId(), "ORGANIZATION_DELETED", "Organization", id, null);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> members(Long organizationId) {
        accessService.requireMember(organizationId);
        getOrg(organizationId);
        return membershipRepository.findAllByOrganizationId(organizationId).stream()
                .map(MembershipResponse::from)
                .toList();
    }

    @Transactional
    public MembershipResponse addMember(Long organizationId, String email, MemberRole role) {
        if (role == MemberRole.ORG_ADMIN) {
            throw new ForbiddenException("ORG_ADMIN cannot be assigned this way");
        }
        accessService.requireRole(organizationId, MemberRole.ORG_ADMIN);
        if (role == MemberRole.PROJECT_MANAGER) {
            accessService.requireRole(organizationId, MemberRole.ORG_ADMIN);
        }
        Organization organization = getOrg(organizationId);
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found. They must sign in once so Keycloak identity is provisioned."));
        if (membershipRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new ConflictException("User is already a member of this organization");
        }
        Membership membership = membershipRepository.save(Membership.builder()
                .organization(organization)
                .user(user)
                .role(role)
                .build());
        auditService.record(organizationId, accessService.currentUserId(), "MEMBER_ADDED", "Membership", membership.getId(), email);
        return MembershipResponse.from(membership);
    }

    @Transactional
    public MembershipResponse updateMemberRole(Long organizationId, Long userId, MemberRole role) {
        if (role == MemberRole.ORG_ADMIN) {
            throw new ForbiddenException("ORG_ADMIN cannot be assigned this way");
        }
        accessService.requireRole(organizationId, MemberRole.ORG_ADMIN);
        Membership membership = membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        if (membership.getRole() == MemberRole.ORG_ADMIN) {
            throw new ForbiddenException("ORG_ADMIN role cannot be changed this way");
        }
        MemberRole previous = membership.getRole();
        membership.setRole(role);
        Membership saved = membershipRepository.save(membership);
        auditService.record(
                organizationId,
                accessService.currentUserId(),
                "MEMBER_ROLE_CHANGED",
                "Membership",
                saved.getId(),
                previous.name() + "->" + role.name()
        );
        return MembershipResponse.from(saved);
    }

    @Transactional
    public OrganizationResponse updateStatus(Long id, OrganizationStatus status) {
        accessService.requireRoleAllowSuspended(id, MemberRole.ORG_ADMIN);
        Organization organization = getOrg(id);
        OrganizationStatus previous = organization.getStatus();
        organization.setStatus(status);
        Organization saved = organizationRepository.save(organization);
        auditService.record(
                id,
                accessService.currentUserId(),
                "ORGANIZATION_STATUS_CHANGED",
                "Organization",
                id,
                previous.name() + "->" + status.name()
        );
        return OrganizationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> deadLetters(Long organizationId) {
        accessService.requireRole(organizationId, MemberRole.ORG_ADMIN);
        getOrg(organizationId);
        return jobRepository.findByOrganizationIdAndStatusOrderByUpdatedAtDesc(organizationId, JobStatus.FAILED)
                .stream()
                .map(JobResponse::from)
                .toList();
    }

    public Organization getOrg(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
