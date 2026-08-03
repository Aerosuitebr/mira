package com.prospectportal.module.outreach.repository;

import com.prospectportal.module.outreach.entity.OutreachMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutreachMessageRepository extends JpaRepository<OutreachMessage, UUID> {

    List<OutreachMessage> findByLeadIdOrderByCreatedAtDesc(UUID leadId);

    @Query("""
        SELECT m FROM OutreachMessage m
        JOIN FETCH m.lead l
        JOIN FETCH l.company
        WHERE m.prospectJobId = :jobId AND m.status = 'PENDING'
        ORDER BY m.createdAt ASC
        """)
    List<OutreachMessage> findPendingByJobId(@Param("jobId") UUID jobId);

    Optional<OutreachMessage> findFirstByProspectJobIdAndStatusOrderByCreatedAtAsc(UUID jobId, String status);

    @Query("""
        SELECT COUNT(m) FROM OutreachMessage m
        WHERE m.channel = 'WHATSAPP'
          AND m.status = 'SENT'
          AND m.sentAt >= :since
        """)
    long countWhatsAppSentSince(@Param("since") Instant since);
}
