package com.prospectportal.module.outreach;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.module.prospect.ProspectCopyBuilder;
import com.prospectportal.module.evolution.EvolutionClient;
import com.prospectportal.module.whatsapp.WhatsAppConnectionService;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.OutreachSettingsRequest;
import com.prospectportal.web.dto.OutreachSettingsResponse;
import com.prospectportal.web.dto.ApprovalNotificationTestResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OutreachSettingsService {

    private static final int MAX_IMAGE_CHARS = 1_400_000; // ~1 MB em base64
    private static final Set<String> ALLOWED_MIME = Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");

    private final AuthContext authContext;
    private final TenantRepository tenantRepository;
    private final EvolutionClient evolutionClient;
    private final WhatsAppConnectionService whatsAppConnectionService;

    public OutreachSettingsService(
        AuthContext authContext,
        TenantRepository tenantRepository,
        EvolutionClient evolutionClient,
        WhatsAppConnectionService whatsAppConnectionService
    ) {
        this.authContext = authContext;
        this.tenantRepository = tenantRepository;
        this.evolutionClient = evolutionClient;
        this.whatsAppConnectionService = whatsAppConnectionService;
    }

    @Transactional(readOnly = true)
    public OutreachSettingsResponse get() {
        Tenant tenant = requireTenant();
        boolean hasImage = tenant.getBrandImageBase64() != null && !tenant.getBrandImageBase64().isBlank();
        return new OutreachSettingsResponse(
            tenant.getOutreachSenderName(),
            hasImage,
            tenant.getBrandImageMime(),
            tenant.getBrandImageFileName(),
            hasImage ? tenant.getBrandImageBase64() : null,
            tenant.getOutreachApprovalRecipient1(),
            tenant.getOutreachApprovalRecipient2()
        );
    }

    @Transactional
    public OutreachSettingsResponse update(OutreachSettingsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe os dados de envio");
        }
        Tenant tenant = requireTenant();

        String sender = request.senderName() != null ? request.senderName().trim() : "";
        if (sender.length() > 120) {
            throw new ResponseStatusException(BAD_REQUEST, "Nome do remetente muito longo (máx. 120 caracteres)");
        }
        tenant.setOutreachSenderName(sender.isBlank() ? null : sender);
        tenant.setOutreachApprovalRecipient1(normalizePhone(request.approvalRecipient1()));
        tenant.setOutreachApprovalRecipient2(normalizePhone(request.approvalRecipient2()));

        boolean clear = Boolean.TRUE.equals(request.clearBrandImage());
        if (clear) {
            tenant.setBrandImageBase64(null);
            tenant.setBrandImageMime(null);
            tenant.setBrandImageFileName(null);
        } else if (request.brandImageBase64() != null && !request.brandImageBase64().isBlank()) {
            String raw = stripDataUri(request.brandImageBase64());
            if (raw.length() > MAX_IMAGE_CHARS) {
                throw new ResponseStatusException(BAD_REQUEST, "Imagem muito grande. Use até cerca de 1 MB.");
            }
            String mime = normalizeMime(request.brandImageMime());
            if (!ALLOWED_MIME.contains(mime)) {
                throw new ResponseStatusException(BAD_REQUEST, "Formato inválido. Use PNG, JPEG ou WebP.");
            }
            String fileName = request.brandImageFileName();
            if (fileName == null || fileName.isBlank()) {
                fileName = mime.contains("png") ? "marca.png" : (mime.contains("webp") ? "marca.webp" : "marca.jpg");
            }
            if (fileName.length() > 120) {
                fileName = fileName.substring(0, 120);
            }
            tenant.setBrandImageBase64(raw);
            tenant.setBrandImageMime(mime);
            tenant.setBrandImageFileName(fileName.trim());
        }

        tenantRepository.save(tenant);
        return get();
    }

    @Transactional(readOnly = true)
    public BrandProfile resolveBrandForCurrentTenant() {
        return resolveBrand(authContext.tenantId());
    }

    @Transactional(readOnly = true)
    public BrandProfile resolveBrand(java.util.UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .map(this::toBrand)
            .orElseGet(BrandProfile::defaults);
    }

    private BrandProfile toBrand(Tenant tenant) {
        String sender = tenant.getOutreachSenderName();
        if (sender == null || sender.isBlank()) {
            sender = BrandProfile.DEFAULT_SENDER;
        }
        ProspectCopyBuilder.LogoAsset logo = null;
        if (tenant.getBrandImageBase64() != null && !tenant.getBrandImageBase64().isBlank()) {
            logo = new ProspectCopyBuilder.LogoAsset(
                tenant.getBrandImageBase64(),
                tenant.getBrandImageMime() != null ? tenant.getBrandImageMime() : "image/png",
                tenant.getBrandImageFileName() != null ? tenant.getBrandImageFileName() : "marca.png"
            );
        }
        return new BrandProfile(sender.trim(), logo);
    }

    private Tenant requireTenant() {
        return tenantRepository.findById(authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant não encontrado"));
    }

    private static String stripDataUri(String value) {
        String trimmed = value.trim();
        int comma = trimmed.indexOf(',');
        if (trimmed.startsWith("data:") && comma > 0) {
            return trimmed.substring(comma + 1).replaceAll("\\s", "");
        }
        return trimmed.replaceAll("\\s", "");
    }

    private static String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return "image/png";
        }
        String normalized = mime.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }

    public ApprovalNotificationTestResponse sendApprovalNotificationTest() {
        Tenant tenant = requireTenant();
        var recipients = java.util.stream.Stream.of(tenant.getOutreachApprovalRecipient1(), tenant.getOutreachApprovalRecipient2())
            .filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (recipients.isEmpty()) return new ApprovalNotificationTestResponse(0, 0, "Cadastre ao menos um responsável.");

        String instance = whatsAppConnectionService.resolveSendInstance();
        var status = evolutionClient.connectionStatus(instance);
        if (!status.connected()) {
            return new ApprovalNotificationTestResponse(
                recipients.size(),
                0,
                "WhatsApp do tenant não está conectado. Reconecte antes de testar."
            );
        }

        int sent = 0;
        String error = null;
        for (String recipient : recipients) {
            var result = evolutionClient.sendInternalNotification(
                instance,
                recipient,
                "Teste MIRA: este número receberá links de aprovação quando um lead responder à etapa 1."
            );
            if (result.success()) sent++;
            else error = result.error();
        }
        return new ApprovalNotificationTestResponse(recipients.size(), sent, error);
    }

    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 12 || digits.length() > 15) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe os responsáveis com DDI e DDD.");
        }
        return digits;
    }

    public record BrandProfile(String senderName, ProspectCopyBuilder.LogoAsset logo) {
        public static final String DEFAULT_SENDER = "Departamento Comercial · Aero Suite";

        public static BrandProfile defaults() {
            return new BrandProfile(DEFAULT_SENDER, null);
        }

        public Optional<ProspectCopyBuilder.LogoAsset> logoOptional() {
            return Optional.ofNullable(logo);
        }
    }
}
