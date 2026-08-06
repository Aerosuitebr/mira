package com.prospectportal.module.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class EvolutionClient {

    private static final Logger log = LoggerFactory.getLogger(EvolutionClient.class);

    private final boolean enabled;
    private final boolean outboundEnabled;
    private final boolean internalNotificationsEnabled;
    private final String apiBaseUrl;
    private final String apiKey;
    private final String defaultInstance;
    private final String publicBaseUrl;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    public EvolutionClient(
        @Value("${app.evolution.enabled:false}") boolean enabled,
        @Value("${app.evolution.outbound-enabled:false}") boolean outboundEnabled,
        @Value("${app.evolution.internal-notifications-enabled:false}") boolean internalNotificationsEnabled,
        @Value("${app.evolution.api-base-url:http://localhost:18082}") String apiBaseUrl,
        @Value("${app.evolution.api-key:}") String apiKey,
        @Value("${app.evolution.instance:mira-prospect}") String instance,
        @Value("${app.public-base-url:http://localhost:4201}") String publicBaseUrl,
        @Value("${app.evolution.webhook-secret:}") String webhookSecret,
        ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.outboundEnabled = outboundEnabled;
        this.internalNotificationsEnabled = internalNotificationsEnabled;
        this.apiBaseUrl = trimSlash(apiBaseUrl);
        this.apiKey = apiKey;
        this.defaultInstance = instance;
        this.publicBaseUrl = trimSlash(publicBaseUrl);
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled && apiBaseUrl != null && !apiBaseUrl.isBlank()
            && apiKey != null && !apiKey.isBlank()
            && !"none".equalsIgnoreCase(apiBaseUrl)
            && !"none".equalsIgnoreCase(apiKey);
    }

    public String instanceName() {
        return defaultInstance;
    }

    public ChannelStatus connectionStatus() {
        return connectionStatus(defaultInstance);
    }

    public ChannelStatus connectionStatus(String instance) {
        if (!isEnabled()) {
            return new ChannelStatus(false, "Evolution API desabilitada", null, null);
        }
        String name = resolveInstance(instance);
        try {
            JsonNode node = get("/instance/connectionState/" + encode(name));
            String state = extractState(node);
            boolean open = "open".equalsIgnoreCase(state);
            // connectionState só traz state; o número vem de fetchInstances (ownerJid/number/owner).
            String phone = extractPhone(node);
            if (open && (phone == null || phone.isBlank())) {
                phone = fetchInstancePhone(name);
            }
            return new ChannelStatus(
                open,
                open ? "WhatsApp conectado" : "WhatsApp: " + (state != null ? state : "desconhecido"),
                state,
                blankToNull(phone)
            );
        } catch (Exception ex) {
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Falha ao consultar Evolution ({}): {}", name, detail);
            return new ChannelStatus(false, "Evolution indisponível: " + detail, null, null);
        }
    }

    /**
     * Resolve o número conectado via fetchInstances (ownerJid / number / owner).
     */
    public String fetchInstancePhone(String instance) {
        if (!isEnabled()) {
            return null;
        }
        String name = resolveInstance(instance);
        try {
            JsonNode node = get("/instance/fetchInstances?instanceName=" + encode(name));
            String phone = extractPhoneFromInstances(node, name);
            return blankToNull(phone);
        } catch (Exception ex) {
            log.warn("Falha ao obter telefone da instância {}: {}", name, ex.getMessage());
            return null;
        }
    }

    public void ensureInstance(String instance) throws Exception {
        String name = resolveInstance(instance);
        if (instanceExists(name)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("instanceName", name);
        body.put("qrcode", true);
        body.put("integration", "WHATSAPP-BAILEYS");
        post("/instance/create", body);
        log.info("Instância Evolution criada: {}", name);
    }

    public boolean instanceExists(String instance) {
        String name = resolveInstance(instance);
        try {
            JsonNode node = get("/instance/fetchInstances?instanceName=" + encode(name));
            if (node == null) {
                return false;
            }
            if (node.isArray()) {
                return !node.isEmpty();
            }
            return node.has("instance") || node.has("name") || node.has("instanceName");
        } catch (Exception ex) {
            // fallback: tenta connectionState; 404 = não existe
            try {
                get("/instance/connectionState/" + encode(name));
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    public Optional<String> fetchConnectQrBase64() {
        return fetchConnectQrBase64(defaultInstance);
    }

    public Optional<String> fetchConnectQrBase64(String instance) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        String name = resolveInstance(instance);
        try {
            JsonNode node = get("/instance/connect/" + encode(name));
            return extractQr(node);
        } catch (Exception ex) {
            log.warn("Falha ao obter QR Evolution ({}): {}", name, ex.getMessage());
            return Optional.empty();
        }
    }

    public void logout(String instance) throws Exception {
        String name = resolveInstance(instance);
        delete("/instance/logout/" + encode(name));
    }

    public void deleteInstance(String instance) throws Exception {
        String name = resolveInstance(instance);
        try {
            delete("/instance/delete/" + encode(name));
        } catch (Exception ex) {
            log.warn("Falha ao deletar instância {}: {}", name, ex.getMessage());
        }
    }

    public boolean isWhatsAppNumber(String phoneE164) {
        return isWhatsAppNumber(defaultInstance, phoneE164);
    }

    public boolean isWhatsAppNumber(String instance, String phoneE164) {
        if (!isEnabled() || phoneE164 == null || phoneE164.isBlank()) {
            return false;
        }
        String name = resolveInstance(instance);
        try {
            Map<String, Object> body = Map.of("numbers", List.of(cleanPhone(phoneE164)));
            JsonNode response = post("/chat/whatsappNumbers/" + encode(name), body);
            if (response == null) {
                return false;
            }
            if (response.isArray() && !response.isEmpty()) {
                JsonNode first = response.get(0);
                if (first.has("exists")) {
                    return first.get("exists").asBoolean(false);
                }
                if (first.has("numberExists")) {
                    return first.get("numberExists").asBoolean(false);
                }
            }
            if (response.has("exists")) {
                return response.get("exists").asBoolean(false);
            }
            return response.toString().contains(cleanPhone(phoneE164));
        } catch (Exception ex) {
            log.warn("Falha ao verificar número WA {}: {}", phoneE164, ex.getMessage());
            return false;
        }
    }

    public SendResult sendText(String phoneE164, String text) {
        return sendText(defaultInstance, phoneE164, text);
    }

    public SendResult sendText(String instance, String phoneE164, String text) {
        if (!outboundEnabled) {
            return SendResult.fail("Envios WhatsApp desabilitados na Fase 1");
        }
        if (!isEnabled()) {
            return SendResult.fail("Evolution API desabilitada");
        }
        String name = resolveInstance(instance);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("number", cleanPhone(phoneE164));
            body.put("text", text);
            JsonNode response = post("/message/sendText/" + encode(name), body);
            return SendResult.ok(extractMessageId(response));
        } catch (RateLimitedException ex) {
            return SendResult.rateLimited(ex.getMessage());
        } catch (Exception ex) {
            log.warn("Falha ao enviar WA para {}: {}", phoneE164, ex.getMessage());
            return SendResult.fail(ex.getMessage());
        }
    }

    /** Configura somente o evento de nova mensagem para a instância conectada. */
    public SendResult configureReplyWebhook(String instance) {
        if (!isEnabled()) return SendResult.fail("Evolution API desabilitada");
        if (webhookSecret == null || webhookSecret.isBlank()) return SendResult.fail("APP_EVOLUTION_WEBHOOK_SECRET não configurado");
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("enabled", true);
            body.put("url", publicBaseUrl + "/webhooks/outreach-bot");
            body.put("events", List.of("MESSAGES_UPSERT"));
            body.put("headers", Map.of("X-Webhook-Secret", webhookSecret));
            body.put("base64", false);
            post("/webhook/set/" + encode(resolveInstance(instance)), body);
            return SendResult.ok(null);
        } catch (Exception ex) {
            return SendResult.fail(ex.getMessage());
        }
    }

    /** Alerta operacional para contatos internos; não habilita envios aos leads. */
    public SendResult sendInternalNotification(String phoneE164, String text) {
        return sendInternalNotification(defaultInstance, phoneE164, text);
    }

    /** Alerta interno pela instância do tenant (não pela instância padrão global). */
    public SendResult sendInternalNotification(String instance, String phoneE164, String text) {
        if (!internalNotificationsEnabled) {
            return SendResult.fail("Alertas internos WhatsApp desabilitados");
        }
        return sendTextUnchecked(instance, phoneE164, text);
    }

    private SendResult sendTextUnchecked(String instance, String phoneE164, String text) {
        if (!isEnabled()) return SendResult.fail("Evolution API desabilitada");
        String name = resolveInstance(instance);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("number", cleanPhone(phoneE164));
            body.put("text", text);
            return SendResult.ok(extractMessageId(post("/message/sendText/" + encode(name), body)));
        } catch (RateLimitedException ex) {
            return SendResult.rateLimited(ex.getMessage());
        } catch (Exception ex) {
            log.warn("Falha ao enviar alerta interno para {}: {}", phoneE164, ex.getMessage());
            return SendResult.fail(ex.getMessage());
        }
    }

    public SendResult sendBrandedImageCaption(
        String phoneE164,
        String caption,
        String base64Png,
        String fileName
    ) {
        return sendBrandedImageCaption(defaultInstance, phoneE164, caption, base64Png, fileName);
    }

    public SendResult sendBrandedImageCaption(
        String instance,
        String phoneE164,
        String caption,
        String base64Png,
        String fileName
    ) {
        if (!outboundEnabled) {
            return SendResult.fail("Envios WhatsApp desabilitados na Fase 1");
        }
        if (!isEnabled()) {
            return SendResult.fail("Evolution API desabilitada");
        }
        String name = resolveInstance(instance);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("number", cleanPhone(phoneE164));
            body.put("mediatype", "image");
            body.put("mimetype", "image/png");
            body.put("media", base64Png);
            body.put("fileName", fileName != null ? fileName : "Aero_Claro.png");
            body.put("caption", caption);
            JsonNode response = post("/message/sendMedia/" + encode(name), body);
            return SendResult.ok(extractMessageId(response));
        } catch (RateLimitedException ex) {
            return SendResult.rateLimited(ex.getMessage());
        } catch (Exception ex) {
            log.warn("Falha ao enviar WA (mídia) para {}: {}", phoneE164, ex.getMessage());
            return SendResult.fail(ex.getMessage());
        }
    }

    public static String cleanPhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() >= 12) {
            return digits;
        }
        if (digits.length() == 10 || digits.length() == 11) {
            return "55" + digits;
        }
        return digits;
    }

    private String resolveInstance(String instance) {
        if (instance == null || instance.isBlank()) {
            return defaultInstance;
        }
        return instance.trim();
    }

    private static Optional<String> extractQr(JsonNode node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.hasNonNull("base64")) {
            return Optional.of(node.get("base64").asText());
        }
        if (node.has("qrcode") && node.get("qrcode").hasNonNull("base64")) {
            return Optional.of(node.get("qrcode").get("base64").asText());
        }
        if (node.has("qrcode") && node.get("qrcode").isTextual()) {
            return Optional.of(node.get("qrcode").asText());
        }
        return Optional.empty();
    }

    private static String extractMessageId(JsonNode response) {
        if (response == null) {
            return null;
        }
        if (response.has("key") && response.get("key").has("id")) {
            return response.get("key").get("id").asText();
        }
        if (response.has("messageId")) {
            return response.get("messageId").asText();
        }
        return null;
    }

    private static String extractState(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.has("instance") && node.get("instance").has("state")) {
            return node.get("instance").get("state").asText();
        }
        if (node.has("state")) {
            return node.get("state").asText();
        }
        return Optional.ofNullable(node.get("status")).map(JsonNode::asText).orElse(null);
    }

    private static String extractPhone(JsonNode node) {
        if (node == null) {
            return null;
        }
        String fromFields = firstPhoneField(node, "owner", "ownerJid", "wuid", "number", "phone", "wid");
        if (fromFields != null) {
            return fromFields;
        }
        if (node.has("instance") && node.get("instance").isObject()) {
            return firstPhoneField(node.get("instance"), "owner", "ownerJid", "wuid", "number", "phone", "wid");
        }
        return null;
    }

    private static String extractPhoneFromInstances(JsonNode node, String instanceName) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (matchesInstance(item, instanceName)) {
                    String phone = extractPhone(item);
                    if (phone != null) {
                        return phone;
                    }
                }
            }
            // Sem match de nome: tenta o primeiro item útil.
            for (JsonNode item : node) {
                String phone = extractPhone(item);
                if (phone != null) {
                    return phone;
                }
            }
            return null;
        }
        return extractPhone(node);
    }

    private static boolean matchesInstance(JsonNode item, String instanceName) {
        if (item == null || instanceName == null) {
            return false;
        }
        String candidate = textOrNull(item, "name");
        if (candidate == null) {
            candidate = textOrNull(item, "instanceName");
        }
        if (candidate == null && item.has("instance") && item.get("instance").isObject()) {
            candidate = textOrNull(item.get("instance"), "instanceName");
            if (candidate == null) {
                candidate = textOrNull(item.get("instance"), "name");
            }
        }
        return candidate != null && candidate.equalsIgnoreCase(instanceName);
    }

    private static String firstPhoneField(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                String cleaned = cleanPhone(node.get(field).asText());
                if (!cleaned.isBlank()) {
                    return cleaned;
                }
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node != null && node.hasNonNull(field)) {
            return node.get(field).asText();
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("apikey", apiKey)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseOrThrow(response);
    }

    private JsonNode post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + path))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .header("apikey", apiKey)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseOrThrow(response);
    }

    private JsonNode delete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("apikey", apiKey)
            .header("Accept", "application/json")
            .DELETE()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseOrThrow(response);
    }

    private JsonNode parseOrThrow(HttpResponse<String> response) throws Exception {
        int code = response.statusCode();
        String body = response.body();
        if (code == 429 || (body != null && body.toLowerCase().contains("rate"))) {
            throw new RateLimitedException("Rate limit Evolution HTTP " + code + ": " + truncate(body));
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(friendlyHttpError(code, body));
        }
        if (body == null || body.isBlank()) {
            return null;
        }
        return objectMapper.readTree(body);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 280 ? value : value.substring(0, 280) + "…";
    }

    private static String friendlyHttpError(int code, String body) {
        String raw = body == null ? "" : body;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("\"exists\":false") || lower.contains("\"exists\": false")) {
            return "Número sem WhatsApp (não encontrado na rede)";
        }
        return "Evolution HTTP " + code + ": " + truncate(raw);
    }

    public record ChannelStatus(boolean connected, String label, String rawState, String phone) {
        public ChannelStatus(boolean connected, String label, String rawState) {
            this(connected, label, rawState, null);
        }
    }

    public record SendResult(boolean success, boolean rateLimited, String messageId, String error) {
        public static SendResult ok(String messageId) {
            return new SendResult(true, false, messageId, null);
        }

        public static SendResult fail(String error) {
            return new SendResult(false, false, null, error);
        }

        public static SendResult rateLimited(String error) {
            return new SendResult(false, true, null, error);
        }
    }

    private static class RateLimitedException extends RuntimeException {
        RateLimitedException(String message) {
            super(message);
        }
    }
}
