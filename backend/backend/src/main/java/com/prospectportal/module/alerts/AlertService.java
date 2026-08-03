package com.prospectportal.module.alerts;

import com.prospectportal.module.alerts.repository.TriggerAlertRepository;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.AlertResponse;
import com.prospectportal.web.mapper.DtoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AlertService {

    private final AuthContext authContext;
    private final TriggerAlertRepository alertRepository;
    private final MarketAlertSyncService marketAlertSyncService;

    public AlertService(
        AuthContext authContext,
        TriggerAlertRepository alertRepository,
        MarketAlertSyncService marketAlertSyncService
    ) {
        this.authContext = authContext;
        this.alertRepository = alertRepository;
        this.marketAlertSyncService = marketAlertSyncService;
    }

    /**
     * Listagem rápida: só lê o que já está persistido.
     * A sincronização de mercado roda em background para não travar o dashboard.
     */
    public List<AlertResponse> listAlerts() {
        UUID tenantId = authContext.tenantId();
        marketAlertSyncService.syncTenantAsync(tenantId);
        return alertRepository.findByTenantIdOrderByTriggeredAtDesc(tenantId)
            .stream()
            .map(DtoMapper::toAlert)
            .toList();
    }

    @Transactional
    public AlertResponse markAsRead(UUID alertId) {
        var alert = alertRepository.findById(alertId)
            .filter(a -> a.getTenant().getId().equals(authContext.tenantId()))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Alerta não encontrado"));
        alert.setRead(true);
        return DtoMapper.toAlert(alertRepository.save(alert));
    }
}
