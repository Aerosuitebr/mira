package com.prospectportal.module.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "cnae_main", nullable = false, length = 10)
    private String cnaeMain;

    @Column(name = "cnae_description")
    private String cnaeDescription;

    @Column(name = "cnae_secondary", length = 2000)
    private String cnaeSecondary;

    @Column(name = "legal_nature")
    private String legalNature;

    @Column(name = "capital_social")
    private BigDecimal capitalSocial;

    @Column(name = "opened_at")
    private LocalDate openedAt;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    private String neighborhood;
    private String street;

    @Column(name = "zip_code", length = 8)
    private String zipCode;

    private Double latitude;
    private Double longitude;

    @Column(name = "estimated_revenue")
    private String estimatedRevenue;

    private String website;

    @Column(name = "registration_status", length = 2)
    private String registrationStatus;

    @Column(name = "company_size_code", length = 2)
    private String companySizeCode;

    private String email;
    private String phone;

    @Column(name = "municipality_code", length = 10)
    private String municipalityCode;

    @Column(name = "data_source", length = 50)
    private String dataSource;

    @Column(name = "location_precision", length = 16)
    private String locationPrecision;

    @Column(nullable = false)
    private boolean geocoded;

    @Column(name = "web_contactable", nullable = false)
    private boolean webContactable;

    @Column(name = "web_probe_status", length = 20)
    private String webProbeStatus;

    @Column(name = "web_probed_at")
    private Instant webProbedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
