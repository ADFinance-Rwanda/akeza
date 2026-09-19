package com.tenant.management.repository;

import com.tenant.management.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByOrganizationIdAndUserIdAndKeyValue(Long organizationId, Long userId, String keyValue);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from IdempotencyKey k
            where k.id = :id and k.responseStatus = 0 and k.createdAt < :cutoff
            """)
    int deleteStaleInProgress(@Param("id") Long id, @Param("cutoff") LocalDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from IdempotencyKey k
            where k.responseStatus <> 0 and k.completedAt is not null and k.completedAt < :cutoff
            """)
    int deleteCompletedBefore(@Param("cutoff") LocalDateTime cutoff);
}
