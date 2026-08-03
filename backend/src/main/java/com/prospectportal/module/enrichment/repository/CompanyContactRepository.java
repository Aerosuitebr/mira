package com.prospectportal.module.enrichment.repository;

import com.prospectportal.module.enrichment.entity.CompanyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyContactRepository extends JpaRepository<CompanyContact, UUID> {
    List<CompanyContact> findByCompanyIdOrderByConfidenceDesc(UUID companyId);

    void deleteByCompanyId(UUID companyId);
}
