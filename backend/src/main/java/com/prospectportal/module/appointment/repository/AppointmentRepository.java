package com.prospectportal.module.appointment.repository;

import com.prospectportal.module.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.tenant.id = :tenantId
        AND a.startsAt >= :from
        AND a.startsAt <= :to
        ORDER BY a.startsAt ASC
        """)
    List<Appointment> findByTenantAndRange(
        @Param("tenantId") UUID tenantId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.tenant.id = :tenantId
        ORDER BY a.startsAt DESC
        """)
    List<Appointment> findByTenantOrderByStartsAtDesc(@Param("tenantId") UUID tenantId);

    Optional<Appointment> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(value = """
        SELECT * FROM appointments a
        WHERE a.status = 'SCHEDULED'
        AND a.reminder_sent_at IS NULL
        AND a.starts_at - (a.reminder_minutes_before * INTERVAL '1 minute') <= NOW()
        """, nativeQuery = true)
    List<Appointment> findDueForReminder();
}
