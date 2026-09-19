package com.tenant.management.controller;

import com.tenant.management.dto.AnalyticsResponse;
import com.tenant.management.dto.CreateProjectRequest;
import com.tenant.management.dto.CreateTaskRequest;
import com.tenant.management.dto.PageResponse;
import com.tenant.management.dto.ProjectResponse;
import com.tenant.management.dto.TaskResponse;
import com.tenant.management.dto.UpdateProjectRequest;
import com.tenant.management.dto.UpdateTaskRequest;
import com.tenant.management.entity.TaskPriority;
import com.tenant.management.entity.TaskStatus;
import com.tenant.management.service.AccessService;
import com.tenant.management.service.AnalyticsService;
import com.tenant.management.service.IdempotencyService;
import com.tenant.management.service.ProjectService;
import com.tenant.management.service.RateLimitService;
import com.tenant.management.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Work")
public class WorkController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final AnalyticsService analyticsService;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;
    private final AccessService accessService;

    @GetMapping("/api/projects")
    @Operation(summary = "List projects in the current organization")
    public ResponseEntity<List<ProjectResponse>> projects() {
        return ResponseEntity.ok(projectService.list());
    }

    @PostMapping("/api/projects")
    @Operation(summary = "Create a project (ORG_ADMIN/PROJECT_MANAGER)")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @GetMapping("/api/projects/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.get(id));
    }

    @PutMapping("/api/projects/{id}")
    @Operation(summary = "Update a project (ORG_ADMIN/PROJECT_MANAGER)")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ResponseEntity.ok(projectService.update(id, request));
    }

    @DeleteMapping("/api/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/tasks")
    @Operation(summary = "Page tasks in the current organization")
    public ResponseEntity<PageResponse<TaskResponse>> listOrgTasks(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long assigneeUserId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) LocalDate dueAfter,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PageResponse.from(taskService.search(
                projectId, status, priority, assigneeUserId, overdue, dueAfter, dueBefore, q, pageable(page, size))));
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    @Operation(summary = "Page tasks for a project with optional filters")
    public ResponseEntity<PageResponse<TaskResponse>> listTasks(
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long assigneeUserId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) LocalDate dueAfter,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PageResponse.from(taskService.search(
                projectId, status, priority, assigneeUserId, overdue, dueAfter, dueBefore, q, pageable(page, size))));
    }

    @GetMapping("/api/projects/{projectId}/tasks/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long projectId, @PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.get(projectId, taskId));
    }

    @GetMapping("/api/tasks/{taskId}")
    @Operation(summary = "Get a task in the current organization")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.get(taskId));
    }

    @PostMapping("/api/tasks")
    @Operation(summary = "Create a task (body.projectId). Requires Idempotency-Key.")
    public ResponseEntity<TaskResponse> createTask(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletResponse response
    ) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        return createTask(request.getProjectId(), idempotencyKey, request, response);
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    @Operation(summary = "Create a task in a project. Requires Idempotency-Key.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created, or idempotent replay of the stored create result"),
            @ApiResponse(responseCode = "400", description = "Validation failed or Idempotency-Key missing"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Keycloak JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member or MEMBER writing another user's task"),
            @ApiResponse(responseCode = "404", description = "Project not found in the current organization"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key reused with a different body"),
            @ApiResponse(responseCode = "429", description = "Write rate limit exceeded"),
            @ApiResponse(responseCode = "503", description = "Redis unavailable for rate limiting")
    })
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletResponse response
    ) {
        Long orgId = accessService.currentOrganizationId();
        rateLimitService.check(orgId + ":" + accessService.currentUserId() + ":POST:/tasks", response);
        return idempotencyService.execute(
                orgId,
                accessService.currentUserId(),
                idempotencyKey,
                request,
                TaskResponse.class,
                () -> taskService.create(projectId, request)
        );
    }

    @PutMapping("/api/projects/{projectId}/tasks/{taskId}")
    @Operation(summary = "Update a task. Requires version for optimistic locking.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or version missing"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Keycloak JWT"),
            @ApiResponse(responseCode = "403", description = "Not allowed to mutate this task"),
            @ApiResponse(responseCode = "404", description = "Task not found in the current organization"),
            @ApiResponse(responseCode = "409", description = "Stale version"),
            @ApiResponse(responseCode = "429", description = "Write rate limit exceeded"),
            @ApiResponse(responseCode = "503", description = "Redis unavailable for rate limiting")
    })
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            HttpServletResponse response
    ) {
        Long orgId = accessService.currentOrganizationId();
        rateLimitService.check(orgId + ":" + accessService.currentUserId() + ":PUT:/tasks", response);
        return ResponseEntity.ok(taskService.update(projectId, taskId, request));
    }

    @PutMapping("/api/tasks/{taskId}")
    @Operation(summary = "Update a task by id. Requires version.")
    public ResponseEntity<TaskResponse> updateTaskById(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            HttpServletResponse response
    ) {
        Long orgId = accessService.currentOrganizationId();
        rateLimitService.check(orgId + ":" + accessService.currentUserId() + ":PUT:/tasks", response);
        return ResponseEntity.ok(taskService.update(taskId, request));
    }

    @DeleteMapping("/api/projects/{projectId}/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            HttpServletResponse response
    ) {
        Long orgId = accessService.currentOrganizationId();
        rateLimitService.check(orgId + ":" + accessService.currentUserId() + ":DELETE:/tasks", response);
        taskService.delete(projectId, taskId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> deleteTaskById(@PathVariable Long taskId, HttpServletResponse response) {
        Long orgId = accessService.currentOrganizationId();
        rateLimitService.check(orgId + ":" + accessService.currentUserId() + ":DELETE:/tasks", response);
        taskService.delete(taskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/analytics/dashboard")
    @Operation(summary = "Organization task analytics (Redis cached)")
    public ResponseEntity<AnalyticsResponse> dashboard() {
        return ResponseEntity.ok(analyticsService.dashboard());
    }

    private Pageable pageable(int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
        }
        return PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
    }
}
