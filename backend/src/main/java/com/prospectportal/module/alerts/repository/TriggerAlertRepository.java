package com.prospectportal.module.alerts.repository;

import com.prospectportal.module.alerts.entity.TriggerAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TriggerAlertRepository extends JpaRepository<TriggerAlert, UUID> {

    @Query("""
        SELECT a FROM TriggerAlert a
        LEFT JOIN FETCH a.company
        LEFT JOIN FETCH a.appointment
        WHERE a.tenant.id = :tenantId
        ORDER BY a.triggeredAt DESC
        """)
    List<TriggerAlert> findByTenantIdOrderByTriggeredAtDesc(@Param("tenantId") UUID tenantId);

    boolean existsByTenant_IdAndCompany_IdAndAlertType(UUID tenantId, UUID companyId, String alertType);

    boolean existsByTenant_IdAndAlertTypeAndTitleAndTriggeredAtAfter(
        UUID tenantId,
        String alertType,
        String title,
        java.time.Instant triggeredAt
    );
}
