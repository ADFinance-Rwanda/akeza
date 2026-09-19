package com.tenant.management.service;

import com.tenant.management.dto.CreateTaskRequest;
import com.tenant.management.dto.TaskResponse;
import com.tenant.management.dto.UpdateTaskRequest;
import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Project;
import com.tenant.management.entity.Task;
import com.tenant.management.entity.TaskPriority;
import com.tenant.management.entity.TaskStatus;
import com.tenant.management.entity.User;
import com.tenant.management.exception.ConflictException;
import com.tenant.management.exception.ForbiddenException;
import com.tenant.management.exception.ResourceNotFoundException;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.TaskRepository;
import com.tenant.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskService {

    static final Clock CLOCK = Clock.systemUTC();

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final AccessService accessService;
    private final AuditService auditService;
    private final JobService jobService;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public Page<TaskResponse> search(
            Long projectId,
            TaskStatus status,
            TaskPriority priority,
            Long assigneeUserId,
            Boolean overdue,
            LocalDate dueAfter,
            LocalDate dueBefore,
            String q,
            Pageable pageable
    ) {
        Long orgId = accessService.currentOrganizationId();
        if (projectId != null) {
            projectService.getInCurrentOrg(projectId);
        }
        if (assigneeUserId != null && !membershipRepository.existsByOrganizationIdAndUserId(orgId, assigneeUserId)) {
            throw new ResourceNotFoundException("Assignee is not a member of this organization");
        }
        String query = sanitizeQuery(q);
        if (query == null) {
            query = "";
        }
        LocalDate today = LocalDate.now(CLOCK);
        return taskRepository.search(
                orgId,
                projectId,
                status,
                priority,
                assigneeUserId,
                dueAfter,
                dueBefore,
                Boolean.TRUE.equals(overdue),
                today,
                TaskStatus.DONE,
                query,
                pageable
        ).map(TaskResponse::from);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long projectId, Long taskId) {
        return TaskResponse.from(getInProject(projectId, taskId));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long taskId) {
        return TaskResponse.from(getInOrg(taskId));
    }

    @Transactional
    public TaskResponse create(Long projectId, CreateTaskRequest request) {
        Long orgId = accessService.currentOrganizationId();
        accessService.requireRole(orgId, MemberRole.ORG_ADMIN, MemberRole.PROJECT_MANAGER, MemberRole.MEMBER);
        Project project = projectService.getInCurrentOrg(projectId);
        User actor = accessService.currentUser();
        TaskStatus status = request.getStatus() == null ? TaskStatus.TODO : request.getStatus();
        Task task = Task.builder()
                .organization(project.getOrganization())
                .project(project)
                .title(request.getTitle().trim())
                .description(trimToNull(request.getDescription()))
                .status(status)
                .priority(request.getPriority() == null ? TaskPriority.MEDIUM : request.getPriority())
                .assignee(resolveAssignee(orgId, request.getAssigneeUserId()))
                .createdBy(actor)
                .dueDate(request.getDueDate())
                .completedAt(status == TaskStatus.DONE ? LocalDateTime.now(CLOCK) : null)
                .build();
        Task saved = taskRepository.save(task);
        auditService.record(orgId, actor.getId(), "TASK_CREATED", "Task", saved.getId(), saved.getTitle());
        analyticsService.evict(orgId);
        jobService.enqueueAnalyticsInvalidation(orgId);
        return TaskResponse.from(saved);
    }

    @Transactional
    public TaskResponse update(Long taskId, UpdateTaskRequest request) {
        Task task = getInOrg(taskId);
        return update(task.getProject().getId(), taskId, request);
    }

    @Transactional
    public TaskResponse update(Long projectId, Long taskId, UpdateTaskRequest request) {
        Long orgId = accessService.currentOrganizationId();
        Task task = getInProject(projectId, taskId);
        requireCanWrite(task);
        if (!request.getVersion().equals(task.getVersion())) {
            throw new ConflictException("Task was updated by another request. Reload and retry.");
        }
        String before = task.getTitle() + "/" + task.getStatus();
        if (request.getTitle() != null) {
            task.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            task.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getStatus() != null) {
            applyStatus(task, request.getStatus());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getAssigneeUserId() != null) {
            task.setAssignee(resolveAssignee(orgId, request.getAssigneeUserId()));
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        try {
            Task saved = taskRepository.saveAndFlush(task);
            auditService.record(
                    orgId,
                    accessService.currentUserId(),
                    "TASK_UPDATED",
                    "Task",
                    saved.getId(),
                    before + "->" + saved.getTitle() + "/" + saved.getStatus());
            analyticsService.evict(orgId);
            jobService.enqueueAnalyticsInvalidation(orgId);
            return TaskResponse.from(saved);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("Task was updated by another request. Reload and retry.");
        }
    }

    @Transactional
    public void delete(Long taskId) {
        Task task = getInOrg(taskId);
        delete(task.getProject().getId(), taskId);
    }

    @Transactional
    public void delete(Long projectId, Long taskId) {
        Long orgId = accessService.currentOrganizationId();
        Task task = getInProject(projectId, taskId);
        requireCanWrite(task);
        taskRepository.delete(task);
        auditService.record(orgId, accessService.currentUserId(), "TASK_DELETED", "Task", taskId, null);
        analyticsService.evict(orgId);
        jobService.enqueueAnalyticsInvalidation(orgId);
    }

    private void applyStatus(Task task, TaskStatus status) {
        if (status == TaskStatus.DONE && task.getStatus() != TaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now(CLOCK));
        } else if (status != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }
        task.setStatus(status);
    }

    private void requireCanWrite(Task task) {
        Long orgId = accessService.currentOrganizationId();
        MemberRole role = accessService.currentRole(orgId);
        if (role == MemberRole.ORG_ADMIN || role == MemberRole.PROJECT_MANAGER) {
            return;
        }
        Long creatorId = task.getCreatedBy() == null ? null : task.getCreatedBy().getId();
        if (!accessService.currentUserId().equals(creatorId)) {
            throw new ForbiddenException("You can only modify your own tasks");
        }
    }

    private Task getInOrg(Long taskId) {
        Long orgId = accessService.currentOrganizationId();
        return taskRepository.findByIdAndOrganizationId(taskId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private Task getInProject(Long projectId, Long taskId) {
        projectService.getInCurrentOrg(projectId);
        Task task = getInOrg(taskId);
        if (!task.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Task not found");
        }
        return task;
    }

    private User resolveAssignee(Long orgId, Long userId) {
        if (userId == null) {
            return null;
        }
        if (!membershipRepository.existsByOrganizationIdAndUserId(orgId, userId)) {
            throw new ResourceNotFoundException("Assignee is not a member of this organization");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee is not a member of this organization"));
    }

    private String sanitizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200);
        }
        return trimmed.replace("%", "").replace("_", "");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
