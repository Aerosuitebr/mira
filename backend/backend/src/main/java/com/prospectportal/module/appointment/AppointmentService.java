package com.prospectportal.module.appointment;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.entity.User;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.common.repository.UserRepository;
import com.prospectportal.module.appointment.entity.Appointment;
import com.prospectportal.module.appointment.repository.AppointmentRepository;
import com.prospectportal.module.client.entity.Client;
import com.prospectportal.module.client.repository.ClientRepository;
import com.prospectportal.common.validation.BrazilPhoneValidator;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.AppointmentResponse;
import com.prospectportal.web.dto.CreateAppointmentRequest;
import com.prospectportal.web.dto.UpdateAppointmentRequest;
import com.prospectportal.web.mapper.DtoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AppointmentService {

    private static final Set<Integer> ALLOWED_REMINDER_MINUTES = Set.of(5, 10, 15, 30, 60, 120, 1440);

    private final AuthContext authContext;
    private final AppointmentRepository appointmentRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final MeetingUrlService meetingUrlService;

    public AppointmentService(
        AuthContext authContext,
        AppointmentRepository appointmentRepository,
        TenantRepository tenantRepository,
        UserRepository userRepository,
        ClientRepository clientRepository,
        MeetingUrlService meetingUrlService
    ) {
        this.authContext = authContext;
        this.appointmentRepository = appointmentRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.meetingUrlService = meetingUrlService;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(Instant from, Instant to) {
        UUID tenantId = authContext.tenantId();
        List<Appointment> appointments;
        if (from != null && to != null) {
            appointments = appointmentRepository.findByTenantAndRange(tenantId, from, to);
        } else {
            appointments = appointmentRepository.findByTenantOrderByStartsAtDesc(tenantId);
        }
        return appointments.stream().map(DtoMapper::toAppointment).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        return appointmentRepository.findByIdAndTenantId(id, authContext.tenantId())
            .map(DtoMapper::toAppointment)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Compromisso não encontrado"));
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        validateTimes(request.startsAt(), request.endsAt());
        int reminderMinutes = resolveReminderMinutes(request.reminderMinutesBefore());

        Tenant tenant = tenantRepository.findById(authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant não encontrado"));
        User owner = userRepository.findById(authContext.userId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Usuário não encontrado"));

        Client client = resolveClient(request.clientId());
        ClientContactFields contact = resolveContactFields(request, client);

        if (contact.name() == null || contact.name().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe o nome do cliente ou selecione um da carteira");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe o título do compromisso");
        }

        Instant now = Instant.now();
        Appointment appointment = new Appointment();
        appointment.setTenant(tenant);
        appointment.setOwner(owner);
        appointment.setClient(client);
        appointment.setClientName(contact.name());
        appointment.setClientEmail(contact.email());
        appointment.setClientPhone(normalizePhone(contact.phone()));
        appointment.setClientCompany(contact.company());
        appointment.setTitle(request.title().trim());
        appointment.setDescription(trimToNull(request.description()));
        appointment.setLocation(trimToNull(request.location()));
        appointment.setVideoConference(Boolean.TRUE.equals(request.videoConference()));
        appointment.setMeetingUrl(null);
        appointment.setStartsAt(request.startsAt());
        appointment.setEndsAt(request.endsAt());
        appointment.setReminderMinutesBefore(reminderMinutes);
        appointment.setStatus("SCHEDULED");
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);

        appointment = appointmentRepository.save(appointment);
        applyMeetingUrl(appointment, request.videoConference(), request.meetingUrl());
        return DtoMapper.toAppointment(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse update(UUID id, UpdateAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findByIdAndTenantId(id, authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Compromisso não encontrado"));

        if (request.startsAt() != null) {
            validateTimes(request.startsAt(), request.endsAt() != null ? request.endsAt() : appointment.getEndsAt());
        }

        if (request.clientId() != null) {
            Client client = resolveClient(request.clientId());
            appointment.setClient(client);
            ClientContactFields contact = resolveContactFields(
                new CreateAppointmentRequest(
                    request.clientId(),
                    request.clientName(),
                    request.clientEmail(),
                    request.clientPhone(),
                    request.clientCompany(),
                    request.title(),
                    request.description(),
                    request.location(),
                    request.videoConference(),
                    request.meetingUrl(),
                    request.startsAt() != null ? request.startsAt() : appointment.getStartsAt(),
                    request.endsAt(),
                    request.reminderMinutesBefore()
                ),
                client
            );
            appointment.setClientName(contact.name());
            appointment.setClientEmail(contact.email());
            appointment.setClientPhone(normalizePhone(contact.phone()));
            appointment.setClientCompany(contact.company());
        } else {
            if (request.clientName() != null) {
                appointment.setClientName(request.clientName().trim());
            }
            if (request.clientEmail() != null) {
                appointment.setClientEmail(trimToNull(request.clientEmail()));
            }
            if (request.clientPhone() != null) {
                appointment.setClientPhone(normalizePhone(trimToNull(request.clientPhone())));
            }
            if (request.clientCompany() != null) {
                appointment.setClientCompany(trimToNull(request.clientCompany()));
            }
        }

        if (request.title() != null && !request.title().isBlank()) {
            appointment.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            appointment.setDescription(trimToNull(request.description()));
        }
        if (request.location() != null) {
            appointment.setLocation(trimToNull(request.location()));
        }
        if (request.videoConference() != null || request.meetingUrl() != null) {
            boolean videoConference = request.videoConference() != null
                ? request.videoConference()
                : appointment.isVideoConference();
            applyMeetingUrl(appointment, videoConference, request.meetingUrl());
        }
        if (request.startsAt() != null) {
            appointment.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            appointment.setEndsAt(request.endsAt());
        }
        if (request.reminderMinutesBefore() != null) {
            int reminderMinutes = resolveReminderMinutes(request.reminderMinutesBefore());
            if (reminderMinutes != appointment.getReminderMinutesBefore()) {
                appointment.setReminderMinutesBefore(reminderMinutes);
                appointment.setReminderSentAt(null);
            }
        }
        if (request.status() != null && !request.status().isBlank()) {
            appointment.setStatus(request.status().trim().toUpperCase());
        }

        appointment.setUpdatedAt(Instant.now());
        return DtoMapper.toAppointment(appointmentRepository.save(appointment));
    }

    @Transactional
    public void cancel(UUID id) {
        Appointment appointment = appointmentRepository.findByIdAndTenantId(id, authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Compromisso não encontrado"));
        appointment.setStatus("CANCELLED");
        appointment.setUpdatedAt(Instant.now());
        appointmentRepository.save(appointment);
    }

    private void applyMeetingUrl(Appointment appointment, Boolean videoConference, String customMeetingUrl) {
        boolean enabled = Boolean.TRUE.equals(videoConference);
        appointment.setVideoConference(enabled);
        if (!enabled) {
            appointment.setMeetingUrl(null);
            return;
        }

        try {
            String custom = trimToNull(customMeetingUrl);
            if (custom != null) {
                appointment.setMeetingUrl(meetingUrlService.resolveMeetingUrl(true, custom, appointment.getId(), appointment.getTitle()));
                return;
            }
            if (appointment.getMeetingUrl() == null || appointment.getMeetingUrl().isBlank()) {
                appointment.setMeetingUrl(meetingUrlService.resolveMeetingUrl(true, null, appointment.getId(), appointment.getTitle()));
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            return BrazilPhoneValidator.normalizeOptional(phone);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    private Client resolveClient(UUID clientId) {
        if (clientId == null) {
            return null;
        }
        return clientRepository.findDetail(authContext.tenantId(), clientId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cliente não encontrado"));
    }

    private ClientContactFields resolveContactFields(CreateAppointmentRequest request, Client client) {
        if (client != null) {
            String name = client.getTradeName() != null && !client.getTradeName().isBlank()
                ? client.getTradeName()
                : client.getLegalName();
            String company = client.getLegalName();
            return new ClientContactFields(
                name,
                firstNonBlank(request.clientEmail(), client.getEmail()),
                firstNonBlank(request.clientPhone(), client.getPhone()),
                firstNonBlank(request.clientCompany(), company)
            );
        }
        return new ClientContactFields(
            trimToNull(request.clientName()),
            trimToNull(request.clientEmail()),
            trimToNull(request.clientPhone()),
            trimToNull(request.clientCompany())
        );
    }

    private int resolveReminderMinutes(Integer minutes) {
        if (minutes == null) {
            return 30;
        }
        if (!ALLOWED_REMINDER_MINUTES.contains(minutes)) {
            throw new ResponseStatusException(BAD_REQUEST, "Antecedência de aviso inválida");
        }
        return minutes;
    }

    private void validateTimes(Instant startsAt, Instant endsAt) {
        if (startsAt == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe data e hora do compromisso");
        }
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(BAD_REQUEST, "O término deve ser posterior ao início");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String trimmed = trimToNull(preferred);
        if (trimmed != null) {
            return trimmed;
        }
        return trimToNull(fallback);
    }

    private record ClientContactFields(String name, String email, String phone, String company) {
    }
}
