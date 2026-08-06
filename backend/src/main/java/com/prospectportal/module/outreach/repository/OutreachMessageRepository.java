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
        JOIN FETCH m.campaign c
        WHERE c.id = :campaignId AND c.tenant.id = :tenantId
        ORDER BY m.createdAt ASC
        """)
    List<OutreachMessage> findCampaignMessages(@Param("campaignId") UUID campaignId, @Param("tenantId") UUID tenantId);

    @Query("""
        SELECT m FROM OutreachMessage m
        JOIN FETCH m.campaign
        WHERE m.lead.id = :leadId
        ORDER BY m.createdAt DESC
        """)
    List<OutreachMessage> findByLeadIdWithCampaignOrderByCreatedAtDesc(@Param("leadId") UUID leadId);

    @Query("""
        SELECT m FROM OutreachMessage m
        JOIN FETCH m.campaign
        WHERE m.lead.id IN :leadIds
        ORDER BY m.createdAt DESC
        """)
    List<OutreachMessage> findByLeadIdInWithCampaignOrderByCreatedAtDesc(@Param("leadIds") List<UUID> leadIds);

    @Query("""
        SELECT DISTINCT co.id FROM OutreachMessage m
        JOIN m.lead l
        JOIN l.company co
        WHERE m.campaign.tenant.id = :tenantId
          AND co.id IN :companyIds
          AND m.outreachStep = 1
        """)
    List<UUID> findCompanyIdsWithFirstStepAttempt(@Param("tenantId") UUID tenantId,
                                                   @Param("companyIds") List<UUID> companyIds);

    @Query("""
        SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM OutreachMessage m
        WHERE m.campaign.tenant.id = :tenantId
          AND m.recipient = :recipient
          AND m.outreachStep = 1
        """)
    boolean existsFirstStepAttemptToRecipient(@Param("tenantId") UUID tenantId,
                                               @Param("recipient") String recipient);

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
          AND m.status IN ('SENT', 'WAITING_REPLY', 'REPLIED')
          AND m.sentAt >= :since
        """)
    long countWhatsAppSentSince(@Param("since") Instant since);

    @Query("""
        SELECT m FROM OutreachMessage m
        JOIN FETCH m.lead l
        JOIN FETCH l.company
        JOIN FETCH m.campaign c
        JOIN FETCH c.tenant
        WHERE m.recipient = :recipient
          AND m.channel = 'WHATSAPP'
          AND m.status IN ('SENT', 'WAITING_REPLY', 'REPLIED')
          AND m.outreachStep = 1
        ORDER BY m.sentAt DESC
        """)
    List<OutreachMessage> findLatestFirstStepSentTo(@Param("recipient") String recipient);

    boolean existsByReplyToMessageIdAndOutreachStep(UUID replyToMessageId, short outreachStep);

    @Query("""
        SELECT COUNT(m) FROM OutreachMessage m
        WHERE m.campaign.tenant.id = :tenantId
          AND m.channel = 'WHATSAPP'
          AND m.outreachStep = :step
          AND m.status = :status
        """)
    long countByTenantAndStepAndStatus(@Param("tenantId") UUID tenantId, @Param("step") short step, @Param("status") String status);

    @Query("""
        SELECT COUNT(m) FROM OutreachMessage m
        WHERE m.campaign.tenant.id = :tenantId
          AND m.channel = 'WHATSAPP'
          AND m.outreachStep = :step
          AND m.status IN :statuses
        """)
    long countByTenantAndStepAndStatusIn(@Param("tenantId") UUID tenantId, @Param("step") short step,
                                         @Param("statuses") List<String> statuses);

    long countByCampaignIdAndOutreachStepAndStatusIn(UUID campaignId, short outreachStep, List<String> statuses);

    @Query("""
        SELECT COUNT(m) FROM OutreachMessage m
        WHERE m.campaign.tenant.id = :tenantId
          AND m.channel = 'WHATSAPP'
          AND m.outreachStep = 1
          AND m.repliedAt IS NOT NULL
        """)
    long countRepliesByTenant(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT m FROM OutreachMessage m
        JOIN FETCH m.lead l
        JOIN FETCH l.company
        JOIN FETCH m.campaign c
        JOIN FETCH c.tenant
        WHERE c.tenant.id = :tenantId
          AND m.outreachStep = 2
          AND m.status = 'AWAITING_APPROVAL'
        ORDER BY m.createdAt ASC
        """)
    List<OutreachMessage> findFollowUpsAwaitingApproval(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT m FROM OutreachMessage m
        JOIN FETCH m.campaign c
        JOIN FETCH c.tenant
        WHERE m.id = :id AND c.tenant.id = :tenantId
        """)
    Optional<OutreachMessage> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
        SELECT m FROM OutreachMessage m
        JOIN FETCH m.lead l
        JOIN FETCH l.company
        JOIN FETCH m.campaign c
        JOIN FETCH c.tenant
        WHERE m.approvalToken = :token
        """)
    Optional<OutreachMessage> findByApprovalToken(@Param("token") String token);
}
