package com.prospectportal.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(name = "monthly_credits", nullable = false)
    private Integer monthlyCredits;

    @Column(name = "credits_used", nullable = false)
    private Integer creditsUsed;

    @Column(name = "evolution_instance_name", length = 100)
    private String evolutionInstanceName;

    @Column(name = "evolution_connection_state", length = 40)
    private String evolutionConnectionState;

    @Column(name = "evolution_connected_at")
    private java.time.Instant evolutionConnectedAt;

    @Column(name = "evolution_phone", length = 30)
    private String evolutionPhone;

    @Column(name = "outreach_sender_name", length = 120)
    private String outreachSenderName;

    @Column(name = "brand_image_base64", columnDefinition = "TEXT")
    private String brandImageBase64;

    @Column(name = "brand_image_mime", length = 40)
    private String brandImageMime;

    @Column(name = "brand_image_file_name", length = 120)
    private String brandImageFileName;
}
