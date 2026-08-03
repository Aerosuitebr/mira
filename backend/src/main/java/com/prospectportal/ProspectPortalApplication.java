package com.prospectportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.prospectportal.module.enrichment.EnrichmentProperties;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(EnrichmentProperties.class)
public class ProspectPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProspectPortalApplication.class, args);
    }
}
