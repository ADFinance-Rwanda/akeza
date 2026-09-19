package com.tenant.management.repository;

import com.tenant.management.entity.Task;
import com.tenant.management.entity.TaskPriority;
import com.tenant.management.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select t from Task t
            join fetch t.organization
            join fetch t.project
            left join fetch t.assignee
            left join fetch t.createdBy
            where t.id = :id and t.organization.id = :orgId
            """)
    Optional<Task> findByIdAndOrganizationId(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query(
            value = """
                    select t from Task t
                    join fetch t.organization
                    join fetch t.project
                    left join fetch t.assignee
                    left join fetch t.createdBy
                    where t.organization.id = :orgId
                      and (:projectId is null or t.project.id = :projectId)
                      and (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assigneeUserId is null or t.assignee.id = :assigneeUserId)
                      and (cast(:dueAfter as date) is null or t.dueDate >= :dueAfter)
                      and (cast(:dueBefore as date) is null or t.dueDate <= :dueBefore)
                      and (:overdue = false or (t.dueDate < :today and t.status <> :doneStatus))
                      and (:q = '' or lower(t.title) like lower(concat('%', :q, '%')))
                    """,
            countQuery = """
                    select count(t) from Task t
                    where t.organization.id = :orgId
                      and (:projectId is null or t.project.id = :projectId)
                      and (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assigneeUserId is null or t.assignee.id = :assigneeUserId)
                      and (cast(:dueAfter as date) is null or t.dueDate >= :dueAfter)
                      and (cast(:dueBefore as date) is null or t.dueDate <= :dueBefore)
                      and (:overdue = false or (t.dueDate < :today and t.status <> :doneStatus))
                      and (:q = '' or lower(t.title) like lower(concat('%', :q, '%')))
                    """
    )
    Page<Task> search(
            @Param("orgId") Long orgId,
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("assigneeUserId") Long assigneeUserId,
            @Param("dueAfter") LocalDate dueAfter,
            @Param("dueBefore") LocalDate dueBefore,
            @Param("overdue") boolean overdue,
            @Param("today") LocalDate today,
            @Param("doneStatus") TaskStatus doneStatus,
            @Param("q") String q,
            Pageable pageable
    );

    @Query("select t.status, count(t) from Task t where t.organization.id = :orgId group by t.status")
    List<Object[]> countByStatus(@Param("orgId") Long orgId);

    @Query("select t.priority, count(t) from Task t where t.organization.id = :orgId group by t.priority")
    List<Object[]> countByPriority(@Param("orgId") Long orgId);

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndStatus(Long organizationId, TaskStatus status);

    long countByOrganizationIdAndStatusIn(Long organizationId, List<TaskStatus> statuses);

    @Query("""
            select count(t) from Task t
            where t.organization.id = :orgId
              and t.dueDate < :today
              and t.status <> :doneStatus
            """)
    long countOverdue(
            @Param("orgId") Long orgId,
            @Param("today") LocalDate today,
            @Param("doneStatus") TaskStatus doneStatus
    );

    @Query(value = """
            select cast(created_at as date) as bucket, count(*) as cnt
            from tasks
            where organization_id = :orgId and created_at >= :from
            group by cast(created_at as date)
            order by bucket
            """, nativeQuery = true)
    List<Object[]> countCreatedByDay(@Param("orgId") Long orgId, @Param("from") LocalDateTime from);

    @Query(value = """
            select cast(completed_at as date) as bucket, count(*) as cnt
            from tasks
            where organization_id = :orgId
              and completed_at is not null
              and completed_at >= :from
            group by cast(completed_at as date)
            order by bucket
            """, nativeQuery = true)
    List<Object[]> countCompletedByDay(@Param("orgId") Long orgId, @Param("from") LocalDateTime from);

    @Query("""
            select t from Task t join fetch t.organization
            where t.dueDate < :today
              and t.status <> :doneStatus
              and t.overdueNotifiedAt is null
            """)
    List<Task> findUnnotifiedOverdue(@Param("today") LocalDate today, @Param("doneStatus") TaskStatus doneStatus);
}
