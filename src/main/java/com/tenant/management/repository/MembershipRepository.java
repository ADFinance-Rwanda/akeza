package com.tenant.management.repository;

import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);

    Optional<Membership> findByTenantIdAndUserId(Long tenantId, Long userId);

    long countByTenantIdAndRole(Long tenantId, MemberRole role);

    @Query("select m from Membership m join fetch m.user join fetch m.tenant where m.tenant.id = :tenantId")
    List<Membership> findAllByTenantId(@Param("tenantId") Long tenantId);

    @Query("select m from Membership m join fetch m.user join fetch m.tenant where m.user.id = :userId")
    List<Membership> findAllByUserId(@Param("userId") Long userId);

    @Query("""
            select case when count(m1) > 0 then true else false end
            from Membership m1, Membership m2
            where m1.user.id = :userId
              and m2.user.id = :otherUserId
              and m1.tenant.id = m2.tenant.id
            """)
    boolean existsSharedTenant(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}
