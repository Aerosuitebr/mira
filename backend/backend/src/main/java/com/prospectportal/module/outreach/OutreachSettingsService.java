package com.prospectportal.module.outreach;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.module.prospect.ProspectCopyBuilder;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.OutreachSettingsRequest;
import com.prospectportal.web.dto.OutreachSettingsResponse;
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

    public OutreachSettingsService(AuthContext authContext, TenantRepository tenantRepository) {
        this.authContext = authContext;
        this.tenantRepository = tenantRepository;
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
            hasImage ? tenant.getBrandImageBase64() : null
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
