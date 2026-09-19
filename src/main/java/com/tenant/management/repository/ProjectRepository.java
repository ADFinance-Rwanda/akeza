package com.tenant.management.repository;

import com.tenant.management.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<Project> findByIdAndOrganizationId(Long id, Long organizationId);

    @Query("select t.project.id, count(t) from Task t where t.organization.id = :orgId group by t.project.id")
    List<Object[]> countTasksByProject(@Param("orgId") Long orgId);
}
