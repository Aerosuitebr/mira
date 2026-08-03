package com.prospectportal.module.appointment;

import com.prospectportal.module.alerts.entity.TriggerAlert;
import com.prospectportal.module.alerts.repository.TriggerAlertRepository;
import com.prospectportal.module.appointment.entity.Appointment;
import com.prospectportal.module.appointment.repository.AppointmentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class AppointmentReminderScheduler {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter
        .ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        .withZone(ZoneId.of("America/Sao_Paulo"));

    private final AppointmentRepository appointmentRepository;
    private final TriggerAlertRepository alertRepository;

    public AppointmentReminderScheduler(
        AppointmentRepository appointmentRepository,
        TriggerAlertRepository alertRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.alertRepository = alertRepository;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void dispatchReminders() {
        List<Appointment> due = appointmentRepository.findDueForReminder();
        Instant now = Instant.now();

        for (Appointment appointment : due) {
            TriggerAlert alert = new TriggerAlert();
            alert.setTenant(appointment.getTenant());
            alert.setCompany(null);
            alert.setAppointment(appointment);
            alert.setAlertType("APPOINTMENT_REMINDER");
            alert.setTitle("Compromisso em breve: " + appointment.getTitle());
            alert.setDescription(buildDescription(appointment));
            alert.setRead(false);
            alert.setTriggeredAt(now);
            alertRepository.save(alert);

            appointment.setReminderSentAt(now);
            appointmentRepository.save(appointment);
        }
    }

    private String buildDescription(Appointment appointment) {
        String when = TIME_FMT.format(appointment.getStartsAt());
        String client = appointment.getClientName();
        if (appointment.getClientCompany() != null && !appointment.getClientCompany().isBlank()) {
            client += " · " + appointment.getClientCompany();
        }
        String location = appointment.getLocation() != null ? " · " + appointment.getLocation() : "";
        String meeting = appointment.isVideoConference() && appointment.getMeetingUrl() != null
            ? " · Videoconferência: " + appointment.getMeetingUrl()
            : "";
        return client + " · " + when + location + meeting;
    }
}
