package com.tenant.management.dto;

import com.tenant.management.entity.ProjectStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectRequest {
    @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String description;

    private ProjectStatus status;
}
