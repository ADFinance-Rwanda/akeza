package com.tenant.management.dto;

import com.tenant.management.entity.Project;
import com.tenant.management.entity.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private Long organizationId;
    private String name;
    private String description;
    private ProjectStatus status;
    private Long taskCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project project) {
        return from(project, null);
    }

    public static ProjectResponse from(Project project, Long taskCount) {
        return ProjectResponse.builder()
                .id(project.getId())
                .organizationId(project.getOrganization().getId())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .taskCount(taskCount)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
