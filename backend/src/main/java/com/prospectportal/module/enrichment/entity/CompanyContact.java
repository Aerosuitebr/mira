package com.prospectportal.module.enrichment.entity;

import com.prospectportal.module.discovery.entity.Company;
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
@Table(name = "company_contacts")
@Getter
@Setter
public class CompanyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "role_title")
    private String roleTitle;

    private String email;
    private String phone;
    private String whatsapp;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    private String source;

    @Column(nullable = false)
    private short confidence;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
