package com.prospectportal.module.whatsapp;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.module.evolution.EvolutionClient;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.WhatsAppConnectionResponse;
import com.prospectportal.web.dto.WhatsAppSendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class WhatsAppConnectionService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConnectionService.class);

    private final AuthContext authContext;
    private final TenantRepository tenantRepository;
    private final EvolutionClient evolutionClient;

    public WhatsAppConnectionService(
        AuthContext authContext,
        TenantRepository tenantRepository,
        EvolutionClient evolutionClient
    ) {
        this.authContext = authContext;
        this.tenantRepository = tenantRepository;
        this.evolutionClient = evolutionClient;
    }

    @Transactional
    public WhatsAppConnectionResponse status() {
        Tenant tenant = requireTenant();
        if (!evolutionClient.isEnabled()) {
            return response(
                tenant,
                false,
                false,
                "provider_offline",
                "Provedor WhatsApp indisponível",
                null,
                "A Evolution API não está configurada neste ambiente. Contate o administrador ou verifique APP_EVOLUTION_ENABLED."
            );
        }

        String instance = resolveOrPreviewInstanceName(tenant);
        var live = evolutionClient.connectionStatus(instance);
        String state = normalizeState(live.rawState(), live.connected());
        persistLiveState(tenant, state, live.phone(), live.connected());

        // Enquanto aguarda pareamento, devolve QR fresco (o da Evolution expira em ~20-40s).
        String qr = null;
        if (!live.connected() && ("connecting".equals(state) || "qr".equals(state))) {
            qr = evolutionClient.fetchConnectQrBase64(instance).orElse(null);
            if (qr != null) {
                state = "qr";
            }
        }

        String hint = switch (state) {
            case "open", "connected" -> "Seu WhatsApp está conectado. Os envios de Abordar usam este número.";
            case "connecting", "qr" -> "No celular: WhatsApp > Aparelhos conectados > Conectar um aparelho. O QR renova sozinho.";
            case "close", "closed", "disconnected" -> "Desconectado. Clique em Conectar para gerar um novo QR Code.";
            default -> live.label();
        };

        return response(
            tenant,
            true,
            live.connected(),
            state,
            statusLabel(state, live.label()),
            qr,
            hint,
            live.phone()
        );
    }

    @Transactional
    public WhatsAppConnectionResponse connect() {
        Tenant tenant = requireTenant();
        if (!evolutionClient.isEnabled()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Evolution API desabilitada");
        }

        String instance = ensureInstanceName(tenant);
        try {
            // Sessão presa em connecting/close: recria para gerar QR válido.
            var existing = evolutionClient.connectionStatus(instance);
            String raw = existing.rawState() != null ? existing.rawState().toLowerCase(Locale.ROOT) : "";
            if (!existing.connected() && ("close".equals(raw) || "closed".equals(raw))) {
                evolutionClient.deleteInstance(instance);
            }
            evolutionClient.ensureInstance(instance);
        } catch (Exception ex) {
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Falha ao garantir instância {}: {}", instance, detail);
            persistLiveState(tenant, "disconnected", null, false);
            // Não lançar 4xx aqui: o interceptor do front trata 401 como logout.
            return response(
                tenant,
                true,
                false,
                "disconnected",
                "Falha ao iniciar sessão",
                null,
                "Não foi possível criar a sessão WhatsApp deste workspace (" + detail
                    + "). Verifique a Evolution API e tente de novo."
            );
        }

        var live = evolutionClient.connectionStatus(instance);
        if (live.connected()) {
            persistLiveState(tenant, "open", live.phone(), true);
            return response(
                tenant,
                true,
                true,
                "open",
                "WhatsApp conectado",
                null,
                "Sessão ativa neste workspace. Os envios de Abordar usam o número do seu aparelho.",
                live.phone()
            );
        }

        String qr = null;
        for (int attempt = 0; attempt < 4 && qr == null; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(800L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            qr = evolutionClient.fetchConnectQrBase64(instance).orElse(null);
        }
        persistLiveState(tenant, qr != null ? "qr" : "connecting", null, false);

        return response(
            tenant,
            true,
            false,
            qr != null ? "qr" : "connecting",
            qr != null ? "Aguardando leitura do QR Code" : "Gerando QR Code…",
            qr,
            qr != null
                ? "No celular do usuário: WhatsApp > Aparelhos conectados > Conectar um aparelho e escaneie o QR."
                : "Não foi possível obter o QR agora. Aguarde alguns segundos e use Gerar novo QR."
        );
    }

    public WhatsAppConnectionResponse configureReplyWebhook() {
        Tenant tenant = requireTenant();
        String instance = resolveSendInstanceForTenant(tenant.getId());
        var result = evolutionClient.configureReplyWebhook(instance);
        if (!result.success()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, result.error());
        }
        return status();
    }

    @Transactional
    public WhatsAppConnectionResponse refreshQr() {
        return connect();
    }

    @Transactional
    public WhatsAppConnectionResponse disconnect() {
        Tenant tenant = requireTenant();
        if (!evolutionClient.isEnabled()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Evolution API desabilitada");
        }
        String instance = tenant.getEvolutionInstanceName();
        if (instance == null || instance.isBlank()) {
            return response(
                tenant,
                true,
                false,
                "disconnected",
                "Nenhuma sessão ativa",
                null,
                "Não há WhatsApp vinculado a este workspace."
            );
        }

        try {
            evolutionClient.logout(instance);
        } catch (Exception ex) {
            log.warn("Logout Evolution {}: {}", instance, ex.getMessage());
        }
        try {
            evolutionClient.deleteInstance(instance);
        } catch (Exception ignored) {
            // logout já encerra a sessão na maioria dos builds
        }

        tenant.setEvolutionConnectionState("disconnected");
        tenant.setEvolutionConnectedAt(null);
        tenant.setEvolutionPhone(null);
        tenantRepository.save(tenant);

        return response(
            tenant,
            true,
            false,
            "disconnected",
            "WhatsApp desconectado",
            null,
            "Sessão encerrada. Conecte novamente quando quiser retomar os envios."
        );
    }

    @Transactional(readOnly = true)
    public WhatsAppSendResponse sendDirectMessage(String phone, String message) {
        if (!evolutionClient.isEnabled()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Evolution API desabilitada");
        }
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe o telefone do destinatário");
        }
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Digite a mensagem a enviar");
        }
        String trimmed = message.trim();
        if (trimmed.length() > 4000) {
            throw new ResponseStatusException(BAD_REQUEST, "Mensagem muito longa (máx. 4000 caracteres)");
        }

        String instance = resolveSendInstance();
        var live = evolutionClient.connectionStatus(instance);
        if (!live.connected()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "WhatsApp desconectado. Conecte em Conexões → WhatsApp e tente novamente."
            );
        }

        String clean = EvolutionClient.cleanPhone(phone);
        if (clean == null || clean.isBlank() || clean.length() < 12) {
            throw new ResponseStatusException(BAD_REQUEST, "Telefone inválido para WhatsApp");
        }

        var result = evolutionClient.sendText(instance, clean, trimmed);
        if (!result.success()) {
            return new WhatsAppSendResponse(false, clean, null, result.error() != null ? result.error() : "Falha no envio");
        }
        return new WhatsAppSendResponse(true, clean, result.messageId(), null);
    }

    /** Instância usada nos envios do tenant autenticado (ou default global se ainda não provisionada). */
    @Transactional
    public String resolveSendInstance() {
        return resolveSendInstanceForTenant(authContext.tenantId());
    }

    @Transactional
    public String resolveSendInstanceForTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant não encontrado"));
        if (tenant.getEvolutionInstanceName() != null && !tenant.getEvolutionInstanceName().isBlank()) {
            return tenant.getEvolutionInstanceName();
        }
        String name = "mira-" + shortId(tenant.getId());
        tenant.setEvolutionInstanceName(name);
        tenant.setEvolutionConnectionState("created");
        tenantRepository.save(tenant);
        return name;
    }

    private Tenant requireTenant() {
        return tenantRepository.findById(authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Tenant não encontrado"));
    }

    private String ensureInstanceName(Tenant tenant) {
        if (tenant.getEvolutionInstanceName() != null && !tenant.getEvolutionInstanceName().isBlank()) {
            return tenant.getEvolutionInstanceName();
        }
        String name = "mira-" + shortId(tenant.getId());
        tenant.setEvolutionInstanceName(name);
        tenant.setEvolutionConnectionState("created");
        tenantRepository.save(tenant);
        return name;
    }

    private String resolveOrPreviewInstanceName(Tenant tenant) {
        if (tenant.getEvolutionInstanceName() != null && !tenant.getEvolutionInstanceName().isBlank()) {
            return tenant.getEvolutionInstanceName();
        }
        return "mira-" + shortId(tenant.getId());
    }

    private void persistLiveState(Tenant tenant, String state, String phone, boolean connected) {
        tenant.setEvolutionConnectionState(state);
        if (phone != null && !phone.isBlank()) {
            tenant.setEvolutionPhone(phone);
        }
        if (connected && tenant.getEvolutionConnectedAt() == null) {
            tenant.setEvolutionConnectedAt(Instant.now());
        }
        tenantRepository.save(tenant);
    }

    private WhatsAppConnectionResponse response(
        Tenant tenant,
        boolean providerEnabled,
        boolean connected,
        String status,
        String statusLabel,
        String qr,
        String hint
    ) {
        return response(tenant, providerEnabled, connected, status, statusLabel, qr, hint, null);
    }

    private WhatsAppConnectionResponse response(
        Tenant tenant,
        boolean providerEnabled,
        boolean connected,
        String status,
        String statusLabel,
        String qr,
        String hint,
        String livePhone
    ) {
        String connectedAt = tenant.getEvolutionConnectedAt() != null
            ? DateTimeFormatter.ISO_INSTANT.format(tenant.getEvolutionConnectedAt())
            : null;
        String phone = livePhone != null && !livePhone.isBlank()
            ? livePhone
            : tenant.getEvolutionPhone();
        return new WhatsAppConnectionResponse(
            providerEnabled,
            connected,
            status,
            statusLabel,
            tenant.getEvolutionInstanceName(),
            phone,
            normalizeQr(qr),
            hint,
            connectedAt
        );
    }

    private static String normalizeQr(String qr) {
        if (qr == null || qr.isBlank()) {
            return null;
        }
        if (qr.startsWith("data:")) {
            return qr;
        }
        return "data:image/png;base64," + qr.replace("data:image/png;base64,", "");
    }

    private static String normalizeState(String raw, boolean connected) {
        if (connected) {
            return "open";
        }
        if (raw == null || raw.isBlank()) {
            return "disconnected";
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private static String statusLabel(String state, String fallback) {
        return switch (state) {
            case "open", "connected" -> "Conectado";
            case "qr", "connecting" -> "Aguardando QR Code";
            case "close", "closed", "disconnected" -> "Desconectado";
            case "provider_offline" -> "Provedor offline";
            default -> fallback != null ? fallback : state;
        };
    }

    private static String shortId(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
}
