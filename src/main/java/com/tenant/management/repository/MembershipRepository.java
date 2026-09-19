package com.tenant.management.repository;

import com.tenant.management.entity.MemberRole;
import com.tenant.management.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

    Optional<Membership> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    long countByOrganizationIdAndRole(Long organizationId, MemberRole role);

    @Query("select m from Membership m join fetch m.user join fetch m.organization where m.organization.id = :organizationId")
    List<Membership> findAllByOrganizationId(@Param("organizationId") Long organizationId);

    @Query("select m from Membership m join fetch m.user join fetch m.organization where m.user.id = :userId")
    List<Membership> findAllByUserId(@Param("userId") Long userId);
}
