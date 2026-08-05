package com.prospectportal.module.prospect;

import com.prospectportal.module.outreach.OutreachSettingsService.BrandProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Copy comercial: e-mail HTML + WhatsApp com marca do tenant (ou fallback Aero Suite).
 * Não usar travessão em textos enviados ao cliente.
 */
@Component
public class ProspectCopyBuilder {

    public static final String LOGO_RESOURCE = "branding/Aero_Claro.png";
    /** Content-ID do logo inline no e-mail (Gmail bloqueia data: URI). */
    public static final String EMAIL_LOGO_CID = "aero-suite-logo";
    public static final String DEFAULT_APPROACH = ApproachId.DIRECT.name();

    public enum ApproachId {
        DIRECT("Contato direto", "Abertura pessoal acompanhando o trabalho da empresa"),
        ANAC("Foco ANAC", "Ênfase em FIFO, OS e preparação para auditoria"),
        DEMO("Demo 15 min", "Convite curto para demonstração objetiva"),
        CONSULTIVE("Consultivo", "Tom de descoberta sobre estoque e ordens de serviço");

        private final String label;
        private final String description;

        ApproachId(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public static ApproachId from(String raw) {
            if (raw == null || raw.isBlank()) {
                return DIRECT;
            }
            try {
                return ApproachId.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return DIRECT;
            }
        }
    }

    public record ApproachCopy(
        String id,
        String label,
        String description,
        String greeting,
        String body,
        String subject
    ) {
        public String fullMessage() {
            String g = greeting != null ? greeting.trim() : "";
            String b = body != null ? body.trim() : "";
            if (g.isEmpty()) {
                return b;
            }
            if (b.isEmpty()) {
                return g;
            }
            return g + "\n\n" + b;
        }
    }

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
        return emailSubject(companyName, brand, ApproachId.DIRECT);
    }

    public String emailSubject(String companyName, BrandProfile brand, ApproachId approach) {
        ApproachId id = approach != null ? approach : ApproachId.DIRECT;
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;
        String company = companyName != null && !companyName.isBlank() ? companyName.trim() : "sua empresa";
        return switch (id) {
            case ANAC -> sender + " · Estoque FIFO, OS e auditoria ANAC na " + company;
            case DEMO -> sender + " · 15 minutos para mostrar o fluxo da " + company;
            case CONSULTIVE -> sender + " · Uma conversa sobre o controle operacional da " + company;
            case DIRECT -> sender + " · Uma conversa sobre organizar o fluxo da " + company;
        };
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
        return whatsappCaption(companyName, contactName, cityState, segment, brand, ApproachId.DIRECT);
    }

    public String whatsappCaption(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand,
        ApproachId approach
    ) {
        return normalizeWhatsAppCopy(
            buildApproach(ApproachId.from(approach != null ? approach.name() : null), true, companyName, contactName, cityState, segment, brand)
                .fullMessage()
        );
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
        return whatsappCaption(companyName, contactName, cityState, segment, brand, ApproachId.DIRECT);
    }

    /** Segunda etapa, usada apenas após uma resposta real recebida no webhook. */
    public String whatsappFollowUp(String companyName, BrandProfile brand) {
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;
        String company = shortCompanyName(companyName);
        return "Obrigado por responder! Sou da " + sender + ". "
            + "Ajudamos operações como a " + company
            + " a organizar comercial, estoque FIFO e Ordens de Serviço no mesmo fluxo. "
            + "Se fizer sentido, posso explicar em uma conversa rápida.\n\n"
            + "Site: " + productUrl;
    }

    /** Entrada curta, sem URL: única copy permitida na primeira mensagem fria. */
    public String whatsappStep1(String companyName) {
        return "Olá, boa tarde! Tudo bem? Nesse contato falo com o responsável comercial da "
            + shortCompanyName(companyName) + "?";
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

    public List<ApproachCopy> listApproaches(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand,
        String channel
    ) {
        boolean whatsapp = channel == null || channel.isBlank() || "WHATSAPP".equalsIgnoreCase(channel);
        List<ApproachCopy> list = new ArrayList<>();
        for (ApproachId id : ApproachId.values()) {
            list.add(buildApproach(id, whatsapp, companyName, contactName, cityState, segment, brand));
        }
        return list;
    }

    public ApproachCopy buildApproach(
        ApproachId approach,
        boolean whatsapp,
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        ApproachId id = approach != null ? approach : ApproachId.DIRECT;
        if (whatsapp) {
            return buildWhatsAppApproach(id, companyName);
        }
        return buildEmailApproach(id, companyName, contactName, cityState, segment, brand);
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

    private ApproachCopy buildWhatsAppApproach(ApproachId id, String companyName) {
        String company = shortCompanyName(companyName);
        return switch (id) {
            case ANAC -> new ApproachCopy(
                id.name(),
                id.label(),
                id.description(),
                "Olá, boa tarde! Tudo bem?",
                "Nesse contato falo com o responsável pela operação e preparação ANAC da " + company + "?",
                null
            );
            case DEMO -> new ApproachCopy(
                id.name(),
                id.label(),
                id.description(),
                "Olá! Tudo bem?",
                "Falo com quem cuida do comercial ou da operação da " + company + "?",
                null
            );
            case CONSULTIVE -> new ApproachCopy(
                id.name(),
                id.label(),
                id.description(),
                "Olá, boa tarde!",
                "Posso falar com a pessoa responsável por estoque ou Ordens de Serviço da " + company + "?",
                null
            );
            case DIRECT -> new ApproachCopy(
                id.name(),
                id.label(),
                id.description(),
                "Olá, boa tarde! Tudo bem?",
                "Nesse contato falo com o responsável pelo comercial ou hangar da " + company + "?",
                null
            );
        };
    }

    private ApproachCopy buildEmailApproach(
        ApproachId id,
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand
    ) {
        String who = emailGreetingName(contactName);
        String place = cityState != null && !cityState.isBlank() ? cityState : "sua região";
        String seg = segment != null && !segment.isBlank() ? segment : "seu segmento";
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;
        String company = companyName != null && !companyName.isBlank() ? companyName.trim() : "sua empresa";
        String subject = emailSubject(company, brand, id);
        String greeting = "Olá " + who + ",";

        String body = switch (id) {
            case ANAC -> """
                Sou %s.

                Identificamos a %s (%s · %s) e queremos ajudar a unificar comercial, estoque FIFO e Ordens de Serviço no mesmo ambiente, com menos planilha e mais disciplina para auditorias da ANAC.

                Em uma demonstração objetiva, mostramos rastreabilidade, OS e visão gerencial em um só fluxo.

                Agende: %s
                Plataforma: %s

                Atenciosamente,
                %s
                %s
                """.formatted(sender, company, place, seg, demoUrl, productUrl, sender, supportEmail).trim();
            case DEMO -> """
                Sou %s.

                Quero propor 15 minutos para mostrar à %s como comercial, estoque e operações podem ficar no mesmo ambiente.

                Se fizer sentido, agende aqui: %s
                Ou conheça a plataforma: %s

                Atenciosamente,
                %s
                %s
                """.formatted(sender, company, demoUrl, productUrl, sender, supportEmail).trim();
            case CONSULTIVE -> """
                Sou %s.

                Acompanhei a %s (%s · %s) e fiquei curioso sobre como vocês organizam estoque e Ordens de Serviço hoje.

                Se fizer sentido, posso te mostrar em 15 minutos uma forma de unificar comercial, estoque e conformidade no mesmo ambiente.

                Agende: %s
                Site: %s

                Atenciosamente,
                %s
                %s
                """.formatted(sender, company, place, seg, demoUrl, productUrl, sender, supportEmail).trim();
            case DIRECT -> """
                Sou %s.

                Identificamos a %s (%s · %s) e gostaríamos de apresentar uma oportunidade concreta: conhecer uma ferramenta poderosa para organizar todo o fluxo da empresa (comercial, estoque, operações e rastreabilidade).

                Em uma demonstração de 30 minutos, mostramos como unificar proposta, ordem de serviço, estoque e conformidade em um único ambiente, com visão global e disciplina operacional.

                Agende sua demonstração: %s
                Conheça a plataforma: %s

                Ficamos à disposição.

                Atenciosamente,
                %s
                %s
                """.formatted(sender, company, place, seg, demoUrl, productUrl, sender, supportEmail).trim();
        };

        return new ApproachCopy(id.name(), id.label(), id.description(), greeting, body, subject);
    }

    private static String emailGreetingName(String contactName) {
        if (contactName == null || contactName.isBlank() || isNonPersonContactLabel(contactName)) {
            return "Prezado(a)";
        }
        return contactName.trim();
    }

    /** Nome fantasia curto; evita razão social gigante no WhatsApp. */
    private static String shortCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "sua empresa";
        }
        String name = companyName.trim()
            .replaceAll("(?i)\\s+(LTDA\\.?|LTDA|ME|EPP|EIRELI|S/?A\\.?|S\\.A\\.)$", "")
            .trim();
        if (name.length() > 42) {
            int space = name.lastIndexOf(' ', 42);
            name = (space > 20 ? name.substring(0, space) : name.substring(0, 42)).trim();
        }
        return name.isBlank() ? companyName.trim() : name;
    }

    public String whatsappTestCaption() {
        return whatsappCaption(
            "Helipse Aviation Ltda",
            "Maria Silva",
            "Rio de Janeiro/RJ",
            "Manutenção e reparação de aeronaves"
        );
    }

    public String whatsappTestFallback() {
        return whatsappFallbackPlainText(
            "Helipse Aviation Ltda",
            "Maria Silva",
            "Rio de Janeiro/RJ",
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
        return emailText(companyName, contactName, cityState, segment, brand, ApproachId.DIRECT);
    }

    public String emailText(
        String companyName,
        String contactName,
        String cityState,
        String segment,
        BrandProfile brand,
        ApproachId approach
    ) {
        return buildEmailApproach(
            approach != null ? approach : ApproachId.DIRECT,
            companyName,
            contactName,
            cityState,
            segment,
            brand
        ).fullMessage();
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
        String who;
        if (contactName == null || contactName.isBlank() || isNonPersonContactLabel(contactName)) {
            who = "Prezado(a)";
        } else {
            who = contactName.trim();
        }
        String place = cityState != null && !cityState.isBlank() ? cityState : "sua região";
        String seg = segment != null && !segment.isBlank() ? segment : "seu segmento";
        String sender = brand != null ? brand.senderName() : BrandProfile.DEFAULT_SENDER;
        Optional<LogoAsset> logo = resolveWhatsAppLogo(brand);

        // cid: + anexo inline no MailSenderService (data: URI é bloqueado pelo Gmail)
        String logoBlock = logo.map(asset -> """
                      <div style="margin:0 0 14px 0;">
                        <img src="cid:%s" alt="%s" width="160" style="display:inline-block;max-width:160px;height:auto;border:0;" />
                      </div>
            """.formatted(EMAIL_LOGO_CID, escape(sender))).orElse("");

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

    /**
     * Primeiro nome para saudação. Rótulos de canal do enrichment (Telefone fixo, WhatsApp, etc.)
     * não são pessoas: cai no genérico para nunca gerar "Olá, Telefone!".
     */
    private static String firstName(String contactName) {
        if (contactName == null || contactName.isBlank() || isNonPersonContactLabel(contactName)) {
            return "tudo bem";
        }
        String trimmed = contactName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    /** Contatos gerados pelo crawl/RF com nome de canal, não de pessoa. */
    public static boolean isNonPersonContactLabel(String contactName) {
        if (contactName == null || contactName.isBlank()) {
            return true;
        }
        String n = contactName.trim().toLowerCase(java.util.Locale.ROOT);
        if ("decisor".equals(n) || "equipe".equals(n) || "contato".equals(n)) {
            return true;
        }
        return n.startsWith("telefone")
            || n.startsWith("whatsapp")
            || n.startsWith("e-mail")
            || n.startsWith("email")
            || n.startsWith("linkedin")
            || n.startsWith("instagram")
            || n.contains("cadastral")
            || n.contains("do site")
            || n.contains("da empresa")
            || n.contains("rede social")
            || n.contains("rede profissional");
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

    /**
     * Bytes do logo para anexo CID no e-mail. Retorna vazio se não houver asset.
     */
    public Optional<EmailInlineLogo> resolveEmailInlineLogo(BrandProfile brand) {
        return resolveWhatsAppLogo(brand).map(asset -> {
            String mime = asset.mimeType() != null && !asset.mimeType().isBlank()
                ? asset.mimeType()
                : "image/png";
            String name = asset.fileName() != null && !asset.fileName().isBlank()
                ? asset.fileName()
                : "Aero_Claro.png";
            return new EmailInlineLogo(
                EMAIL_LOGO_CID,
                Base64.getDecoder().decode(asset.base64()),
                mime,
                name
            );
        });
    }

    public record LogoAsset(String base64, String mimeType, String fileName) {}

    public record EmailInlineLogo(String contentId, byte[] bytes, String mimeType, String fileName) {}
}
