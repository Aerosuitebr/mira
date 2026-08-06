package com.prospectportal.module.outreach.repository;

import com.prospectportal.module.outreach.entity.OutreachCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface OutreachCampaignRepository extends JpaRepository<OutreachCampaign, UUID> {
    List<OutreachCampaign> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<OutreachCampaign> findByIdAndTenantId(UUID id, UUID tenantId);
}
