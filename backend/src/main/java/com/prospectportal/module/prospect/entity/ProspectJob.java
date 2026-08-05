package com.prospectportal.module.prospect.entity;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.module.outreach.entity.OutreachCampaign;
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
@Table(name = "prospect_jobs")
@Getter
@Setter
public class ProspectJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private OutreachCampaign campaign;

    @Column(nullable = false)
    private String name;

    private String cnae;
    private String state;
    private String city;
    private String keyword;

    @Column(name = "selected_company_ids", columnDefinition = "TEXT")
    private String selectedCompanyIds;

    @Column(name = "company_limit", nullable = false)
    private int companyLimit = 20;

    @Column(nullable = false)
    private String status = "QUEUED";

    @Column(name = "test_mode", nullable = false)
    private boolean testMode = false;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = false;

    @Column(name = "found_count", nullable = false)
    private int foundCount;

    @Column(name = "enriched_count", nullable = false)
    private int enrichedCount;

    @Column(name = "queued_count", nullable = false)
    private int queuedCount;

    @Column(name = "wa_sent", nullable = false)
    private int waSent;

    @Column(name = "email_sent", nullable = false)
    private int emailSent;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "next_dispatch_at")
    private Instant nextDispatchAt;

    @Column(name = "wa_paused_until")
    private Instant waPausedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
