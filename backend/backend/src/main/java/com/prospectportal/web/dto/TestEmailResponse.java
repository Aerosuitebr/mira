package com.prospectportal.web.dto;

public record TestEmailResponse(
    boolean success,
    String deliveredTo,
    String subject,
    String message,
    String error
) {
    public static TestEmailResponse ok(String deliveredTo, String subject) {
        return new TestEmailResponse(true, deliveredTo, subject, "E-mail premium Aero Suite enviado.", null);
    }

    public static TestEmailResponse fail(String error) {
        return new TestEmailResponse(false, null, null, null, error);
    }
}
