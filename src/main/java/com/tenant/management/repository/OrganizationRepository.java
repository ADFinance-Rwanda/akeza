package com.tenant.management.repository;

import com.tenant.management.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsBySlugIgnoreCase(String slug);

    Optional<Organization> findBySlugIgnoreCase(String slug);
}
