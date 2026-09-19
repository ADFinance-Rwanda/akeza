package com.tenant.management.dto;

import com.tenant.management.entity.TaskPriority;
import com.tenant.management.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateTaskRequest {
    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    private TaskStatus status;
    private TaskPriority priority;
    private Long assigneeUserId;
    private LocalDate dueDate;

    @NotNull(message = "version is required for optimistic locking")
    private Long version;
}
