package com.prospectportal.module.crm.repository;

import com.prospectportal.module.crm.entity.CrmPipeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CrmPipelineRepository extends JpaRepository<CrmPipeline, UUID> {
    Optional<CrmPipeline> findFirstByTenantIdAndDefaultPipelineTrue(UUID tenantId);
}
