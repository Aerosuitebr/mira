package com.prospectportal.module.appointment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

@Component
public class MeetingUrlService {

    private final String jitsiBaseUrl;

    public MeetingUrlService(@Value("${app.meetings.jitsi-base-url:https://meet.jit.si}") String jitsiBaseUrl) {
        this.jitsiBaseUrl = jitsiBaseUrl.replaceAll("/+$", "");
    }

    public String generateJitsiRoom(UUID appointmentId, String title) {
        String slug = "prospectportal-" + appointmentId.toString().replace("-", "").substring(0, 12);
        return jitsiBaseUrl + "/" + slug;
    }

    public String resolveMeetingUrl(boolean videoConference, String customUrl, UUID appointmentId, String title) {
        if (!videoConference) {
            return null;
        }
        String trimmed = trimToNull(customUrl);
        if (trimmed != null) {
            validateHttpUrl(trimmed);
            return trimmed;
        }
        return generateJitsiRoom(appointmentId, title);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validateHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                throw new IllegalArgumentException("Link deve começar com http:// ou https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Link de videoconferência inválido");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Link de videoconferência inválido");
        }
    }
}
