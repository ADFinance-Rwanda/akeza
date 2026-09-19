package com.tenant.management.service;

import com.tenant.management.dto.CreateProjectRequest;
import com.tenant.management.dto.ProjectResponse;
import com.tenant.management.dto.UpdateProjectRequest;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Organization;
import com.tenant.management.entity.Project;
import com.tenant.management.entity.ProjectStatus;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrganizationService organizationService;
    private final AccessService accessService;
    private final AuditService auditService;
    private final JobService jobService;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        Long orgId = accessService.currentOrganizationId();
        Map<Long, Long> counts = projectRepository.countTasksByProject(orgId).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));
        return projectRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .map(project -> ProjectResponse.from(project, counts.getOrDefault(project.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return ProjectResponse.from(getInCurrentOrg(id));
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        Long orgId = accessService.currentOrganizationId();
        accessService.requireRole(orgId, MemberRole.ORG_ADMIN, MemberRole.PROJECT_MANAGER);
        Organization organization = organizationService.getOrg(orgId);
        Project project = projectRepository.save(Project.builder()
                .organization(organization)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .status(request.getStatus() == null ? ProjectStatus.ACTIVE : request.getStatus())
                .build());
        auditService.record(orgId, accessService.currentUserId(), "PROJECT_CREATED", "Project", project.getId(), project.getName());
        analyticsService.evict(orgId);
        jobService.enqueueAnalyticsInvalidation(orgId);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest request) {
        Long orgId = accessService.currentOrganizationId();
        accessService.requireRole(orgId, MemberRole.ORG_ADMIN, MemberRole.PROJECT_MANAGER);
        Project project = getInCurrentOrg(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            project.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        Project saved = projectRepository.save(project);
        auditService.record(orgId, accessService.currentUserId(), "PROJECT_UPDATED", "Project", saved.getId(), saved.getName());
        return ProjectResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = accessService.currentOrganizationId();
        accessService.requireRole(orgId, MemberRole.ORG_ADMIN, MemberRole.PROJECT_MANAGER);
        Project project = getInCurrentOrg(id);
        projectRepository.delete(project);
        auditService.record(orgId, accessService.currentUserId(), "PROJECT_DELETED", "Project", id, null);
        analyticsService.evict(orgId);
        jobService.enqueueAnalyticsInvalidation(orgId);
    }

    public Project getInCurrentOrg(Long id) {
        Long orgId = accessService.currentOrganizationId();
        return projectRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
