package com.prospectportal.web.mapper;

import com.prospectportal.module.alerts.entity.TriggerAlert;
import com.prospectportal.module.appointment.entity.Appointment;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.enrichment.entity.CompanyContact;
import com.prospectportal.module.outreach.entity.OutreachCampaign;
import com.prospectportal.module.outreach.entity.OutreachTemplate;
import com.prospectportal.web.dto.AlertResponse;
import com.prospectportal.web.dto.AppointmentResponse;
import com.prospectportal.web.dto.CampaignResponse;
import com.prospectportal.web.dto.CompanyResponse;
import com.prospectportal.web.dto.ContactResponse;
import com.prospectportal.web.dto.TemplateResponse;

import java.util.UUID;

public final class DtoMapper {

    private DtoMapper() {
    }

    public static CompanyResponse toCompany(Company company) {
        return new CompanyResponse(
            company.getId(),
            company.getCnpj(),
            company.getLegalName(),
            company.getTradeName(),
            company.getCnaeMain(),
            company.getCnaeSecondary(),
            company.getCnaeDescription(),
            company.getCity(),
            company.getState(),
            company.getNeighborhood(),
            company.getStreet(),
            company.getZipCode(),
            company.getCapitalSocial(),
            company.getOpenedAt(),
            company.getEstimatedRevenue(),
            company.getWebsite(),
            company.getEmail(),
            company.getPhone(),
            company.getLatitude(),
            company.getLongitude(),
            company.isGeocoded(),
            company.getLocationPrecision(),
            company.isWebContactable()
        );
    }

    public static ContactResponse toContact(CompanyContact contact) {
        return new ContactResponse(
            contact.getId(),
            contact.getFullName(),
            contact.getRoleTitle(),
            contact.getEmail(),
            contact.getPhone(),
            contact.getWhatsapp(),
            contact.getLinkedinUrl(),
            contact.getWebsiteUrl(),
            contact.getInstagramUrl(),
            contact.getConfidence(),
            contact.getSource()
        );
    }

    public static TemplateResponse toTemplate(OutreachTemplate template) {
        return new TemplateResponse(
            template.getId(),
            template.getName(),
            template.getChannel(),
            template.getSubject(),
            template.getBodyTemplate()
        );
    }

    public static CampaignResponse toCampaign(OutreachCampaign campaign) {
        return new CampaignResponse(
            campaign.getId(),
            campaign.getName(),
            campaign.getChannel(),
            campaign.getStatus(),
            campaign.getSentCount(),
            campaign.getCreatedAt()
        );
    }

    public static AlertResponse toAlert(TriggerAlert alert) {
        Company company = alert.getCompany();
        UUID companyId = company != null ? company.getId() : null;
        String companyName;
        if (company != null) {
            companyName = company.getTradeName() != null ? company.getTradeName() : company.getLegalName();
        } else if (alert.getAppointment() != null) {
            companyName = alert.getAppointment().getClientName();
        } else {
            companyName = "—";
        }
        return new AlertResponse(
            alert.getId(),
            companyId,
            companyName,
            alert.getAlertType(),
            alert.getTitle(),
            alert.getDescription(),
            alert.isRead(),
            alert.getTriggeredAt()
        );
    }

    public static AppointmentResponse toAppointment(Appointment appointment) {
        String ownerName = appointment.getOwner() != null ? appointment.getOwner().getFullName() : null;
        UUID clientId = appointment.getClient() != null ? appointment.getClient().getId() : null;
        return new AppointmentResponse(
            appointment.getId(),
            clientId,
            appointment.getClientName(),
            appointment.getClientEmail(),
            appointment.getClientPhone(),
            appointment.getClientCompany(),
            appointment.getTitle(),
            appointment.getDescription(),
            appointment.getLocation(),
            appointment.isVideoConference(),
            appointment.getMeetingUrl(),
            appointment.getStartsAt(),
            appointment.getEndsAt(),
            appointment.getReminderMinutesBefore(),
            appointment.getReminderSentAt() != null,
            appointment.getStatus(),
            ownerName,
            appointment.getCreatedAt()
        );
    }
}
