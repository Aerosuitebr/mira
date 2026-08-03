package com.prospectportal.module.prospect.repository;

import com.prospectportal.module.prospect.entity.ProspectJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProspectJobRepository extends JpaRepository<ProspectJob, UUID> {

    List<ProspectJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ProspectJob> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
        SELECT j FROM ProspectJob j
        WHERE j.status = 'RUNNING'
        ORDER BY j.nextDispatchAt ASC NULLS FIRST, j.createdAt ASC
        """)
    List<ProspectJob> findRunningJobs();
}
