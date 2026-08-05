package com.prospectportal.module.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MailSenderService {

    private static final Logger log = LoggerFactory.getLogger(MailSenderService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;
    private final String username;
    private final boolean testMode;
    private final String testEmail;

    public MailSenderService(
        JavaMailSender mailSender,
        @Value("${app.mail.from:noreply@aerosuite.com.br}") String from,
        @Value("${app.mail.from-name:Aero Suite}") String fromName,
        @Value("${spring.mail.username:}") String username,
        @Value("${spring.mail.password:}") String password,
        @Value("${app.outreach.test-mode:false}") boolean testMode,
        @Value("${app.outreach.test-email:}") String testEmail
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
        this.username = username;
        this.testMode = testMode;
        this.testEmail = testEmail;
        if (password == null || password.isBlank()) {
            log.warn("SMTP sem senha configurada (SPRING_MAIL_PASSWORD). Envio de e-mail falhará até configurar.");
        }
    }

    public boolean isConfigured() {
        return username != null && !username.isBlank();
    }

    public boolean isTestMode() {
        return testMode;
    }

    public String testEmail() {
        return testEmail;
    }

    public SendResult sendHtml(String to, String subject, String htmlBody, String textBody, String intendedRecipient) {
        return sendHtml(to, subject, htmlBody, textBody, intendedRecipient, null, null);
    }

    public SendResult sendHtml(
        String to,
        String subject,
        String htmlBody,
        String textBody,
        String intendedRecipient,
        String fromNameOverride
    ) {
        return sendHtml(to, subject, htmlBody, textBody, intendedRecipient, fromNameOverride, null);
    }

    public SendResult sendHtml(
        String to,
        String subject,
        String htmlBody,
        String textBody,
        String intendedRecipient,
        String fromNameOverride,
        InlineImage inlineLogo
    ) {
        if (!isConfigured()) {
            return SendResult.fail("SMTP não configurado");
        }
        String destination = resolveDestination(to);
        String finalSubject = testMode ? "[TESTE MIRA] " + subject : subject;
        String finalHtml = wrapTestBanner(htmlBody, intendedRecipient != null ? intendedRecipient : to);
        String finalText = wrapTestBannerText(textBody, intendedRecipient != null ? intendedRecipient : to);
        String displayName = fromNameOverride != null && !fromNameOverride.isBlank() ? fromNameOverride.trim() : fromName;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, displayName, StandardCharsets.UTF_8.name()));
            helper.setTo(destination);
            helper.setReplyTo(username != null && !username.isBlank() ? username : from);
            helper.setSubject(finalSubject);
            helper.setText(finalText != null ? finalText : "", finalHtml);
            if (inlineLogo != null && inlineLogo.bytes() != null && inlineLogo.bytes().length > 0) {
                String cid = inlineLogo.contentId() != null && !inlineLogo.contentId().isBlank()
                    ? inlineLogo.contentId()
                    : "aero-suite-logo";
                String mime = inlineLogo.mimeType() != null && !inlineLogo.mimeType().isBlank()
                    ? inlineLogo.mimeType()
                    : "image/png";
                helper.addInline(cid, new ByteArrayResource(inlineLogo.bytes()) {
                    @Override
                    public String getFilename() {
                        return inlineLogo.fileName() != null && !inlineLogo.fileName().isBlank()
                            ? inlineLogo.fileName()
                            : "logo.png";
                    }
                }, mime);
            }
            mailSender.send(message);
            log.info("E-mail enviado para {} (assunto: {})", destination, finalSubject);
            return SendResult.ok(destination);
        } catch (Exception ex) {
            log.error("Falha ao enviar e-mail para {}: {}", destination, ex.getMessage());
            return SendResult.fail(ex.getMessage());
        }
    }

    public record InlineImage(String contentId, byte[] bytes, String mimeType, String fileName) {
        public static InlineImage from(String contentId, byte[] bytes, String mimeType, String fileName) {
            return new InlineImage(contentId, bytes, mimeType, fileName);
        }
    }

    private String resolveDestination(String to) {
        if (testMode) {
            if (testEmail == null || testEmail.isBlank()) {
                throw new IllegalStateException("Modo teste ativo sem APP_OUTREACH_TEST_EMAIL");
            }
            return testEmail;
        }
        return to;
    }

    private String wrapTestBanner(String html, String intended) {
        if (!testMode) {
            return html;
        }
        String banner = """
            <div style="margin:0 0 20px 0;padding:12px 14px;background:#fef3c7;border:1px solid #f59e0b;border-radius:10px;color:#92400e;font-size:13px;">
              <strong>Modo teste MIRA</strong>: destinatário real seria: %s
            </div>
            """.formatted(escape(intended));
        return banner + (html != null ? html : "");
    }

    private String wrapTestBannerText(String text, String intended) {
        if (!testMode) {
            return text;
        }
        String banner = "[TESTE MIRA] Destinatário real seria: " + intended + "\n\n";
        return banner + (text != null ? text : "");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record SendResult(boolean success, String deliveredTo, String error) {
        public static SendResult ok(String deliveredTo) {
            return new SendResult(true, deliveredTo, null);
        }

        public static SendResult fail(String error) {
            return new SendResult(false, null, error);
        }
    }
}
