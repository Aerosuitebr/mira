package com.prospectportal.module.prospect;

import com.prospectportal.module.outreach.repository.OutreachMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class WhatsAppThrottle {

    private final OutreachMessageRepository messageRepository;
    private final boolean businessHoursOnly;
    private final ZoneId zoneId;
    private final int startHour;
    private final int endHour;
    private final int minIntervalSeconds;
    private final int maxIntervalSeconds;
    private final int hourlyCap;
    private final int dailyCap;
    private final int warmupDay1;
    private final int warmupDay3;
    private final int warmupDay7;
    private final Instant warmupAnchor;

    public WhatsAppThrottle(
        OutreachMessageRepository messageRepository,
        @Value("${app.outreach.whatsapp.business-hours-only:true}") boolean businessHoursOnly,
        @Value("${app.outreach.whatsapp.timezone:America/Sao_Paulo}") String timezone,
        @Value("${app.outreach.whatsapp.start-hour:9}") int startHour,
        @Value("${app.outreach.whatsapp.end-hour:18}") int endHour,
        @Value("${app.outreach.whatsapp.min-interval-seconds:45}") int minIntervalSeconds,
        @Value("${app.outreach.whatsapp.max-interval-seconds:120}") int maxIntervalSeconds,
        @Value("${app.outreach.whatsapp.hourly-cap:5}") int hourlyCap,
        @Value("${app.outreach.whatsapp.daily-cap:30}") int dailyCap,
        @Value("${app.outreach.whatsapp.warmup-day1-cap:10}") int warmupDay1,
        @Value("${app.outreach.whatsapp.warmup-day3-cap:20}") int warmupDay3,
        @Value("${app.outreach.whatsapp.warmup-day7-cap:30}") int warmupDay7
    ) {
        this.messageRepository = messageRepository;
        this.businessHoursOnly = businessHoursOnly;
        this.zoneId = ZoneId.of(timezone);
        this.startHour = startHour;
        this.endHour = endHour;
        this.minIntervalSeconds = minIntervalSeconds;
        this.maxIntervalSeconds = Math.max(minIntervalSeconds, maxIntervalSeconds);
        this.hourlyCap = hourlyCap;
        this.dailyCap = dailyCap;
        this.warmupDay1 = warmupDay1;
        this.warmupDay3 = warmupDay3;
        this.warmupDay7 = warmupDay7;
        this.warmupAnchor = Instant.now();
    }

    public boolean withinBusinessHours() {
        if (!businessHoursOnly) {
            return true;
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        int hour = now.getHour();
        return hour >= startHour && hour < endHour;
    }

    public boolean canSendWhatsApp() {
        if (!withinBusinessHours()) {
            return false;
        }
        Instant hourAgo = Instant.now().minusSeconds(3600);
        Instant dayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();
        long hourly = messageRepository.countWhatsAppSentSince(hourAgo);
        long daily = messageRepository.countWhatsAppSentSince(dayStart);
        return hourly < hourlyCap && daily < effectiveDailyCap();
    }

    public String blockReason() {
        if (!withinBusinessHours()) {
            return "Fora da janela comercial WhatsApp";
        }
        Instant hourAgo = Instant.now().minusSeconds(3600);
        Instant dayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();
        if (messageRepository.countWhatsAppSentSince(hourAgo) >= hourlyCap) {
            return "Cap horário WhatsApp atingido (" + hourlyCap + "/h)";
        }
        if (messageRepository.countWhatsAppSentSince(dayStart) >= effectiveDailyCap()) {
            return "Cap diário WhatsApp atingido (" + effectiveDailyCap() + "/dia)";
        }
        return null;
    }

    public Instant nextSlotAfterSend() {
        int jitter = ThreadLocalRandom.current().nextInt(minIntervalSeconds, maxIntervalSeconds + 1);
        return Instant.now().plusSeconds(jitter);
    }

    public Instant nextBusinessWindow() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime candidate = now.withHour(startHour).withMinute(0).withSecond(0).withNano(0);
        if (!now.toLocalTime().isBefore(candidate.toLocalTime()) || !withinBusinessHours()) {
            candidate = candidate.plusDays(1);
        }
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private int effectiveDailyCap() {
        long days = java.time.Duration.between(warmupAnchor, Instant.now()).toDays() + 1;
        if (days <= 1) {
            return Math.min(dailyCap, warmupDay1);
        }
        if (days <= 3) {
            return Math.min(dailyCap, warmupDay3);
        }
        if (days <= 7) {
            return Math.min(dailyCap, warmupDay7);
        }
        return dailyCap;
    }
}
