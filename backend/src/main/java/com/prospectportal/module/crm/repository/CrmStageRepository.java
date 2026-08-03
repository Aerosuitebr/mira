package com.prospectportal.module.crm.repository;

import com.prospectportal.module.crm.entity.CrmStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CrmStageRepository extends JpaRepository<CrmStage, UUID> {
    List<CrmStage> findByPipelineIdOrderByPositionAsc(UUID pipelineId);
}
