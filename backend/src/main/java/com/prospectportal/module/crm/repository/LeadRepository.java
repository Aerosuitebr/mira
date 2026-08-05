package com.prospectportal.module.crm.repository;

import com.prospectportal.module.crm.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    @Query("""
        SELECT l FROM Lead l
        JOIN FETCH l.company
        WHERE l.tenant.id = :tenantId
        ORDER BY l.updatedAt DESC
        """)
    List<Lead> findByTenantWithCompany(@Param("tenantId") UUID tenantId);
    Optional<Lead> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

    @Query("""
        SELECT l FROM Lead l
        JOIN FETCH l.company
        WHERE l.tenant.id = :tenantId
          AND l.company.id IN :companyIds
        """)
    List<Lead> findByTenantIdAndCompanyIdIn(
        @Param("tenantId") UUID tenantId,
        @Param("companyIds") List<UUID> companyIds
    );
}
