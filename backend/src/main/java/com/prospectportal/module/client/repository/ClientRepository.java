package com.prospectportal.module.client.repository;

import com.prospectportal.module.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

    Optional<Client> findByTenantIdAndLeadId(UUID tenantId, UUID leadId);

    @Query("""
        SELECT c FROM Client c
        JOIN FETCH c.company
        LEFT JOIN FETCH c.owner
        WHERE c.tenant.id = :tenantId
        AND (:status IS NULL OR c.status = :status)
        AND (:serviceStatus IS NULL OR c.serviceStatus = :serviceStatus)
        AND (:minLtv IS NULL OR c.lifetimeValue >= :minLtv)
        AND (:tenureDays IS NULL OR c.contractedAt <= :tenureCutoff)
        ORDER BY c.contractedAt DESC
        """)
    List<Client> findPortfolio(
        @Param("tenantId") UUID tenantId,
        @Param("status") String status,
        @Param("serviceStatus") String serviceStatus,
        @Param("minLtv") java.math.BigDecimal minLtv,
        @Param("tenureDays") Integer tenureDays,
        @Param("tenureCutoff") java.time.Instant tenureCutoff
    );

    @Query("""
        SELECT c FROM Client c
        JOIN FETCH c.company
        LEFT JOIN FETCH c.lead
        LEFT JOIN FETCH c.owner
        WHERE c.id = :id AND c.tenant.id = :tenantId
        """)
    Optional<Client> findDetail(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    long countByTenantId(UUID tenantId);
}
