package com.prospectportal.module.outreach.entity;

import com.prospectportal.module.crm.entity.Lead;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outreach_messages")
@Getter
@Setter
public class OutreachMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private OutreachCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false)
    private String channel;

    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private String status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "replied_at")
    private Instant repliedAt;

    private String provider;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "fallback_of")
    private UUID fallbackOf;

    @Column(name = "outreach_step", nullable = false)
    private short outreachStep = 1;

    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "approval_token", length = 80)
    private String approvalToken;

    @Column(name = "approval_expires_at")
    private Instant approvalExpiresAt;

    @Column(name = "approval_approved_at")
    private Instant approvalApprovedAt;

    @Column(name = "prospect_job_id")
    private UUID prospectJobId;

    private String recipient;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
