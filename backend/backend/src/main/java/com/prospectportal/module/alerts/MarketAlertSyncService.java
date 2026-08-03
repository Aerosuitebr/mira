package com.prospectportal.module.alerts;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.module.alerts.entity.TriggerAlert;
import com.prospectportal.module.alerts.repository.TriggerAlertRepository;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MarketAlertSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketAlertSyncService.class);

    public static final String TYPE_NEW_CNPJ = "NEW_CNPJ";
    public static final String TYPE_SEGMENT_WEEK = "SEGMENT_WEEK";

    private static final int NEW_COMPANY_ALERT_LIMIT = 20;
    private static final int SEGMENT_ALERT_LIMIT = 6;
    private static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(15);

    private final TriggerAlertRepository alertRepository;
    private final CompanyRepository companyRepository;
    private final TenantRepository tenantRepository;
    private final ConcurrentHashMap<UUID, Instant> lastRefreshByTenant = new ConcurrentHashMap<>();

    public MarketAlertSyncService(
        TriggerAlertRepository alertRepository,
        CompanyRepository companyRepository,
        TenantRepository tenantRepository
    ) {
        this.alertRepository = alertRepository;
        this.companyRepository = companyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Async("alertTaskExecutor")
    public void syncTenantAsync(UUID tenantId) {
        try {
            syncTenant(tenantId);
        } catch (Exception ex) {
            log.warn("Sync assíncrono de alertas falhou para tenant {}: {}", tenantId, ex.getMessage());
        }
    }

    @Transactional
    public void syncTenant(UUID tenantId) {
        Instant now = Instant.now();
        Instant last = lastRefreshByTenant.get(tenantId);
        if (last != null && now.isBefore(last.plus(REFRESH_COOLDOWN))) {
            return;
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return;
        }

        Instant periodStart = LocalDate.now(ZoneOffset.UTC).minusDays(365).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Company> recent = companyRepository.findNewestOpeningsNative(NEW_COMPANY_ALERT_LIMIT);
        int created = 0;

        for (Company company : recent) {
            if (alertRepository.existsByTenant_IdAndCompany_IdAndAlertType(tenantId, company.getId(), TYPE_NEW_CNPJ)) {
                continue;
            }
            TriggerAlert alert = new TriggerAlert();
            alert.setTenant(tenant);
            alert.setCompany(company);
            alert.setAlertType(TYPE_NEW_CNPJ);
            alert.setTitle(buildNewCompanyTitle(company));
            alert.setDescription(buildNewCompanyDescription(company));
            alert.setRead(false);
            alert.setTriggeredAt(openedInstant(company));
            alertRepository.save(alert);
            created++;
        }

        recent.stream()
            .filter(c -> c.getState() != null && c.getCnaeMain() != null)
            .collect(Collectors.groupingBy(
                c -> c.getState() + "|" + c.getCnaeMain() + "|"
                    + (c.getCnaeDescription() != null ? c.getCnaeDescription() : c.getCnaeMain()),
                Collectors.counting()
            ))
            .entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(SEGMENT_ALERT_LIMIT)
            .forEach(entry -> {
                String[] parts = entry.getKey().split("\\|", 3);
                String state = parts[0];
                String cnae = parts[1];
                String segment = parts.length > 2 ? parts[2] : cnae;
                long count = entry.getValue();
                String title = state + " · CNAE " + cnae + ": " + count + " aberturas recentes";
                if (alertRepository.existsByTenant_IdAndAlertTypeAndTitleAndTriggeredAtAfter(
                    tenantId,
                    TYPE_SEGMENT_WEEK,
                    title,
                    periodStart
                )) {
                    return;
                }
                TriggerAlert alert = new TriggerAlert();
                alert.setTenant(tenant);
                alert.setCompany(null);
                alert.setAlertType(TYPE_SEGMENT_WEEK);
                alert.setTitle(title);
                alert.setDescription(
                    "No lote mais recente da base, o segmento \"" + truncate(segment, 120)
                        + "\" em " + state + " aparece " + count + " vezes. "
                        + "Use Descobrir com CNAE " + cnae + " e UF " + state + " para abordar."
                );
                alert.setRead(false);
                alert.setTriggeredAt(Instant.now());
                alertRepository.save(alert);
            });

        lastRefreshByTenant.put(tenantId, Instant.now());
        log.info("Alertas de mercado sync tenant {}: {} novos CNPJs", tenantId, created);
    }

    private static String buildNewCompanyTitle(Company company) {
        return "Novo CNPJ em " + company.getCity() + "/" + company.getState();
    }

    private static String buildNewCompanyDescription(Company company) {
        String name = company.getTradeName() != null && !company.getTradeName().isBlank()
            ? company.getTradeName()
            : company.getLegalName();
        String segment = company.getCnaeDescription() != null && !company.getCnaeDescription().isBlank()
            ? company.getCnaeDescription()
            : "CNAE " + company.getCnaeMain();
        String opened = company.getOpenedAt() != null
            ? company.getOpenedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)
            : "data n/d";
        return name + " abriu em " + company.getCity() + "/" + company.getState()
            + " (" + opened + ") · " + truncate(segment, 100);
    }

    private static Instant openedInstant(Company company) {
        if (company.getOpenedAt() == null) {
            return Instant.now();
        }
        return company.getOpenedAt().atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "...";
    }
}
