package com.tenant.management.repository;

import com.tenant.management.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<Project> findByIdAndOrganizationId(Long id, Long organizationId);
}
