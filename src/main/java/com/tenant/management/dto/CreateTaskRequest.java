package com.tenant.management.dto;

import com.tenant.management.entity.TaskPriority;
import com.tenant.management.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTaskRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    private TaskStatus status;
    private TaskPriority priority;
    private Long projectId;
    private Long assigneeUserId;
    private LocalDate dueDate;
}
