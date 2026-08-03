package com.prospectportal.module.outreach.repository;

import com.prospectportal.module.outreach.entity.OutreachTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutreachTemplateRepository extends JpaRepository<OutreachTemplate, UUID> {
    List<OutreachTemplate> findByTenantIdOrderByNameAsc(UUID tenantId);
}
