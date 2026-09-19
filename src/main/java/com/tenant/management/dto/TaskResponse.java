package com.tenant.management.dto;

import com.tenant.management.entity.Task;
import com.tenant.management.entity.TaskPriority;
import com.tenant.management.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private Long organizationId;
    private Long projectId;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private Long assigneeUserId;
    private Long createdByUserId;
    private LocalDate dueDate;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse from(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .organizationId(task.getOrganization().getId())
                .projectId(task.getProject().getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .assigneeUserId(task.getAssignee() == null ? null : task.getAssignee().getId())
                .createdByUserId(task.getCreatedBy() == null ? null : task.getCreatedBy().getId())
                .dueDate(task.getDueDate())
                .version(task.getVersion())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
