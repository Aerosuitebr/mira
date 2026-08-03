package com.prospectportal.web.dto;

public record TestWhatsAppResponse(
    boolean success,
    String phone,
    String instance,
    String mode,
    String messageId,
    String preview,
    String error,
    String qrCodeBase64
) {
    public static TestWhatsAppResponse ok(String phone, String instance, String mode, String messageId, String preview) {
        return new TestWhatsAppResponse(true, phone, instance, mode, messageId, preview, null, null);
    }

    public static TestWhatsAppResponse needsQr(String phone, String instance, String error, String qr) {
        return new TestWhatsAppResponse(false, phone, instance, "needs_qr", null, null, error, qr);
    }

    public static TestWhatsAppResponse fail(String phone, String instance, String error) {
        return new TestWhatsAppResponse(false, phone, instance, "failed", null, null, error, null);
    }
}
