package com.prospectportal.module.project.repository;

import com.prospectportal.module.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<Project> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
        SELECT p FROM Project p
        JOIN FETCH p.client
        WHERE p.tenant.id = :tenantId
        ORDER BY p.updatedAt DESC
        """)
    List<Project> findBoardProjects(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT p FROM Project p
        JOIN FETCH p.client
        WHERE p.id = :id
        """)
    java.util.Optional<Project> findWithClient(@Param("id") UUID id);
}
