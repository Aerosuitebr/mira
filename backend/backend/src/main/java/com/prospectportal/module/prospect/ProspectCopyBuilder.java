package com.prospectportal.module.prospect;

import com.prospectportal.module.outreach.OutreachSettingsService.BrandProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;

/**
 * Copy comercial: e-mail HTML + WhatsApp com marca do tenant (ou fallback Aero Suite).
 * Não usar travessão em textos enviados ao cliente.
 */
@Component
public class ProspectCopyBuilder {

    public static final String LOGO_RESOURCE = "branding/Aero_Claro.png";

    private final String productUrl;
    private final String demoUrl;
    private final String supportEmail;

    public ProspectCopyBuilder(
        @Value("${app.outreach.product-url:https://aerosuite.com.br}") String productUrl,
        @Value("${app.outreach.demo-url:https://aerosuite.com.br/contato/#agendar-demo}") String demoUrl,
        @Value("${app.mail.support-email:contato@aerosuite.com.br}") String supportEmail
    ) {
        this.productUrl = productUrl;
        this.demoUrl = demoUrl;
        this.supportEmail = supportEmail;
    }

    public String emailSubject(String companyName) {
        return emailSubject(companyName, BrandProfile.defaults());
    }

    public String emailSubject(String companyName, BrandProfile brand) {
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;
        return sender + " · Uma conversa sobre organizar o fluxo da " + companyName;
    }

    public String whatsappCaption(String companyName, String contactName, String cityState, String segment) {
        return whatsappCaption(companyName, contactName, cityState, segment, BrandProfile.defaults());
    }

    public String whatsappCaption(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        return normalizeWhatsAppCopy(buildWhatsAppMessage(companyName, contactName, cityState, segment, brand));
    }

    public String whatsappFallbackPlainText(String companyName, String contactName, String cityState, String segment) {
        return whatsappFallbackPlainText(companyName, contactName, cityState, segment, BrandProfile.defaults());
    }

    public String whatsappFallbackPlainText(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;
        return normalizeWhatsAppCopy("""
            *%s*

            %s
            """.formatted(sender, buildWhatsAppMessage(companyName, contactName, cityState, segment, brand)));
    }

    public String whatsappBody(String companyName, String contactName, String cityState, String segment) {
        return whatsappCaption(companyName, contactName, cityState, segment);
    }

    public String whatsappBody(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        return whatsappCaption(companyName, contactName, cityState, segment, brand);
    }

    public static String normalizeWhatsAppCopy(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replaceAll("(?m)^[ \\t]+$", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.replaceAll("(?m)^\\*(Comércio[^*\\n]*)\\*$", "$1");
        text = text.replaceAll("(?m)^\\*(Aero Suite · Comércio[^*\\n]*)\\*$", "$1");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private String buildWhatsAppMessage(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        String firstName = firstName(contactName);
        String place = cityState != null && !cityState.isBlank() ? " em " + cityState.trim() : "";
        String company = companyName != null && !companyName.isBlank() ? companyName.trim() : "sua empresa";
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;

        return """
            Olá, *%s*! 👋

            Identificamos a *%s*%s e gostaríamos de apresentar uma oportunidade concreta: conhecer uma ferramenta para organizar todo o fluxo da empresa, do comercial ao estoque, das operações à conformidade.

            Em poucos minutos de demonstração, você vê como unificar:
            • Propostas e pipeline comercial
            • Estoque com rastreabilidade
            • Operações MRO e disciplina operacional
            • Visão gerencial em um só ambiente

            Posso reservar *15 minutos* para uma demonstração objetiva, sem compromisso?

            📅 Agendar: %s

            Atenciosamente,
            %s
            🌐 %s
            📧 %s
            """.formatted(firstName, company, place, demoUrl, sender, productUrl, supportEmail);
    }

    public String whatsappTestCaption() {
        return whatsappCaption(
            "Oficina Exemplo MRO",
            "Welle",
            "Cabo Frio/RJ",
            "Manutenção e reparação de aeronaves"
        );
    }

    public String whatsappTestFallback() {
        return whatsappFallbackPlainText(
            "Oficina Exemplo MRO",
            "Welle",
            "Cabo Frio/RJ",
            "Manutenção e reparação de aeronaves"
        );
    }

    public Optional<LogoAsset> resolveWhatsAppLogo() {
        return resolveWhatsAppLogo(Optional.empty());
    }

    public Optional<LogoAsset> resolveWhatsAppLogo(Optional<LogoAsset> tenantLogo) {
        if (tenantLogo != null && tenantLogo.isPresent()) {
            return tenantLogo;
        }
        return defaultClasspathLogo();
    }

    public Optional<LogoAsset> resolveWhatsAppLogo(BrandProfile brand) {
        if (brand != null && brand.logo() != null) {
            return Optional.of(brand.logo());
        }
        return defaultClasspathLogo();
    }

    private Optional<LogoAsset> defaultClasspathLogo() {
        try {
            ClassPathResource resource = new ClassPathResource(LOGO_RESOURCE);
            if (!resource.exists()) {
                return Optional.empty();
            }
            try (InputStream in = resource.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length == 0) {
                    return Optional.empty();
                }
                return Optional.of(new LogoAsset(
                    Base64.getEncoder().encodeToString(bytes),
                    "image/png",
                    "Aero_Claro.png"
                ));
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public String emailText(String companyName, String contactName, String cityState, String segment) {
        return emailText(companyName, contactName, cityState, segment, BrandProfile.defaults());
    }

    public String emailText(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        String who = contactName != null && !contactName.isBlank() ? contactName : "Prezado(a)";
        String place = cityState != null && !cityState.isBlank() ? cityState : "sua região";
        String seg = segment != null && !segment.isBlank() ? segment : "seu segmento";
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;

        return """
            Olá %s,

            Sou %s.

            Identificamos a %s (%s · %s) e gostaríamos de apresentar uma oportunidade concreta: conhecer uma ferramenta poderosa para organizar todo o fluxo da empresa (comercial, estoque, operações e rastreabilidade).

            Em uma demonstração de 30 minutos, mostramos como unificar proposta, ordem de serviço, estoque e conformidade em um único ambiente, com visão global e disciplina operacional.

            Agende sua demonstração: %s
            Conheça a plataforma: %s

            Ficamos à disposição.

            Atenciosamente,
            %s
            %s
            """.formatted(who, sender, companyName, place, seg, demoUrl, productUrl, sender, supportEmail).trim();
    }

    public String emailHtml(String companyName, String contactName, String cityState, String segment) {
        return emailHtml(companyName, contactName, cityState, segment, BrandProfile.defaults());
    }

    public String emailHtml(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        String who = contactName != null && !contactName.isBlank() ? contactName : "Prezado(a)";
        String place = cityState != null && !cityState.isBlank() ? cityState : "sua região";
        String seg = segment != null && !segment.isBlank() ? segment : "seu segmento";
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;
        Optional<LogoAsset> logo = resolveWhatsAppLogo(brand);

        String logoBlock = logo.map(asset -> """
                      <div style="margin:0 0 14px 0;">
                        <img src="data:%s;base64,%s" alt="%s" width="160" style="display:inline-block;max-width:160px;height:auto;border:0;" />
                      </div>
            """.formatted(escape(asset.mimeType()), asset.base64(), escape(sender))).orElse("");

        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta name="color-scheme" content="light only">
              <title>%s · Prospecção</title>
            </head>
            <body style="margin:0;padding:0;background:#e2e8f0;font-family:'Segoe UI',Arial,Helvetica,sans-serif;color:#0f172a;">
            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#e2e8f0;padding:32px 12px;">
              <tr><td align="center">
                <table role="presentation" width="680" cellspacing="0" cellpadding="0" border="0" style="max-width:680px;width:100%%;border-collapse:collapse;">

                  <tr>
                    <td bgcolor="#0369a1" style="background-color:#0369a1;border-radius:16px 16px 0 0;padding:14px 20px;text-align:center;">
                      <div style="font-size:12px;font-weight:700;color:#ffffff;letter-spacing:.06em;text-transform:uppercase;">
                        %s
                      </div>
                    </td>
                  </tr>

                  <tr>
                    <td bgcolor="#0f172a" style="background-color:#0f172a;padding:32px 28px 28px 28px;text-align:center;">
                      %s
                      <div style="font-size:28px;font-weight:800;color:#ffffff;line-height:1.15;letter-spacing:-0.02em;">%s</div>
                      <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:16px auto;width:120px;border-collapse:collapse;">
                        <tr><td bgcolor="#e8c547" style="background-color:#e8c547;height:3px;font-size:0;line-height:0;">&nbsp;</td></tr>
                      </table>
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" align="center" style="max-width:560px;margin:0 auto;border-collapse:collapse;">
                        <tr>
                          <td bgcolor="#1e293b" style="background-color:#1e293b;border:1px solid #334155;border-radius:12px;padding:18px 16px;text-align:center;">
                            <div style="font-size:22px;font-weight:800;color:#ffffff;line-height:1.35;margin:0 0 10px 0;">
                              Organize todo o fluxo da %s em uma única plataforma
                            </div>
                            <div style="font-size:15px;color:#e2e8f0;line-height:1.55;margin:0;">
                              Conectamos comércio, estoque e operações com
                              <strong style="color:#ffffff;">rastreabilidade</strong> e disciplina operacional.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>

                  <tr>
                    <td bgcolor="#ffffff" style="background-color:#ffffff;padding:32px 32px 12px 32px;font-size:15px;line-height:1.65;color:#475569;">
                      <p style="margin:0 0 16px 0;font-size:16px;line-height:1.65;color:#334155;">Olá %s,</p>
                      <p style="margin:0 0 16px 0;">
                        Sou <strong>%s</strong>. Identificamos a
                        <strong>%s</strong> (%s · %s) e queremos convidar você a conhecer uma ferramenta
                        que pode ajudar a organizar <strong>todo o fluxo da empresa</strong>, do comercial ao estoque,
                        das operações à conformidade.
                      </p>
                      <p style="margin:0 0 16px 0;">
                        Em uma demonstração objetiva, mostramos como unificar propostas, ordens de serviço,
                        estoque com rastreabilidade e visão gerencial em um só ambiente.
                      </p>

                      <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:8px auto 28px auto;">
                        <tr>
                          <td bgcolor="#0369a1" style="border-radius:10px;">
                            <a href="%s" style="display:inline-block;padding:14px 28px;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;">
                              Agendar demonstração de 30 min
                            </a>
                          </td>
                        </tr>
                      </table>

                      <p style="margin:0 0 8px 0;font-size:14px;color:#64748b;text-align:center;">
                        Ou acesse: <a href="%s" style="color:#0369a1;font-weight:600;">%s</a>
                      </p>
                    </td>
                  </tr>

                  <tr>
                    <td bgcolor="#0f172a" style="background-color:#0f172a;border-radius:0 0 16px 16px;padding:22px 28px;text-align:center;">
                      <div style="font-size:13px;color:#cbd5e1;line-height:1.6;">
                        %s<br>
                        <a href="mailto:%s" style="color:#7dd3fc;text-decoration:none;">%s</a>
                      </div>
                    </td>
                  </tr>

                </table>
              </td></tr>
            </table>
            </body>
            </html>
            """.formatted(
            escape(sender),
            escape(sender),
            logoBlock,
            escape(sender),
            escape(companyName),
            escape(who),
            escape(sender),
            escape(companyName),
            escape(place),
            escape(seg),
            demoUrl,
            productUrl,
            productUrl,
            escape(sender),
            supportEmail,
            supportEmail
        );
    }

    public String sampleCompanyName() {
        return "Oficina Exemplo MRO";
    }

    private static String firstName(String contactName) {
        if (contactName == null || contactName.isBlank() || "decisor".equalsIgnoreCase(contactName) || "equipe".equalsIgnoreCase(contactName)) {
            return "tudo bem";
        }
        String trimmed = contactName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    public record LogoAsset(String base64, String mimeType, String fileName) {}
}
