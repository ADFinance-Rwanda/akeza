package com.tenant.management.repository;

import com.tenant.management.entity.Job;
import com.tenant.management.entity.JobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    @Query("""
            select j from Job j
            where j.status = :status and j.availableAt <= :now
            order by j.createdAt asc
            """)
    List<Job> findDue(@Param("status") JobStatus status, @Param("now") LocalDateTime now, Pageable pageable);

    List<Job> findByOrganizationIdAndStatusOrderByUpdatedAtDesc(Long organizationId, JobStatus status);

    boolean existsByTypeAndStatus(String type, JobStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = :claimed, j.attempts = j.attempts + 1, j.updatedAt = CURRENT_TIMESTAMP
            where j.id = :id and j.status = :expected
            """)
    int claim(@Param("id") Long id, @Param("claimed") JobStatus claimed, @Param("expected") JobStatus expected);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
            set j.status = :pending, j.updatedAt = CURRENT_TIMESTAMP
            where j.status = :processing and j.updatedAt < :stale
            """)
    int reclaimStale(
            @Param("pending") JobStatus pending,
            @Param("processing") JobStatus processing,
            @Param("stale") LocalDateTime stale
    );
}
