package com.prospectportal.module.outreach;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.crm.repository.LeadRepository;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.enrichment.entity.CompanyContact;
import com.prospectportal.module.enrichment.repository.CompanyContactRepository;
import com.prospectportal.module.evolution.EvolutionClient;
import com.prospectportal.module.mail.MailSenderService;
import com.prospectportal.module.outreach.entity.OutreachCampaign;
import com.prospectportal.module.outreach.entity.OutreachMessage;
import com.prospectportal.module.outreach.entity.OutreachTemplate;
import com.prospectportal.module.outreach.repository.OutreachCampaignRepository;
import com.prospectportal.module.outreach.repository.OutreachMessageRepository;
import com.prospectportal.module.outreach.repository.OutreachTemplateRepository;
import com.prospectportal.module.prospect.ProspectCopyBuilder;
import com.prospectportal.module.whatsapp.WhatsAppConnectionService;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.AiCopyRequest;
import com.prospectportal.web.dto.AiCopyResponse;
import com.prospectportal.web.dto.BulkCampaignResponse;
import com.prospectportal.web.dto.BulkOutreachRequest;
import com.prospectportal.web.dto.CampaignResponse;
import com.prospectportal.web.dto.ApproachStatusResponse;
import com.prospectportal.web.dto.DeliveryItem;
import com.prospectportal.web.dto.OutreachMessageHistoryItem;
import com.prospectportal.web.dto.OutreachReportResponse;
import com.prospectportal.web.dto.FollowUpReviewItem;
import com.prospectportal.web.dto.FollowUpApprovalResponse;
import com.prospectportal.web.dto.CampaignDetailResponse;
import com.prospectportal.web.dto.CampaignMessageDetail;
import com.prospectportal.web.dto.UpdateCampaignMessageRequest;
import com.prospectportal.web.dto.UpdateCampaignRequest;
import com.prospectportal.web.dto.TemplateResponse;
import com.prospectportal.web.mapper.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OutreachService {

    private static final Logger log = LoggerFactory.getLogger(OutreachService.class);

    private final AuthContext authContext;
    private final TenantRepository tenantRepository;
    private final CompanyRepository companyRepository;
    private final CompanyContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OutreachTemplateRepository templateRepository;
    private final OutreachCampaignRepository campaignRepository;
    private final OutreachMessageRepository messageRepository;
    private final CrmAutomationService crmAutomationService;
    private final ProspectCopyBuilder copyBuilder;
    private final EvolutionClient evolutionClient;
    private final WhatsAppConnectionService whatsAppConnectionService;
    private final MailSenderService mailSenderService;
    private final OutreachSettingsService outreachSettingsService;
    private final OutreachBotQueueService outreachBotQueueService;
    private final boolean mockAiEnabled;

    public OutreachService(
        AuthContext authContext,
        TenantRepository tenantRepository,
        CompanyRepository companyRepository,
        CompanyContactRepository contactRepository,
        LeadRepository leadRepository,
        OutreachTemplateRepository templateRepository,
        OutreachCampaignRepository campaignRepository,
        OutreachMessageRepository messageRepository,
        CrmAutomationService crmAutomationService,
        ProspectCopyBuilder copyBuilder,
        EvolutionClient evolutionClient,
        WhatsAppConnectionService whatsAppConnectionService,
        MailSenderService mailSenderService,
        OutreachSettingsService outreachSettingsService,
        OutreachBotQueueService outreachBotQueueService,
        @Value("${app.ai.mock-enabled:true}") boolean mockAiEnabled
    ) {
        this.authContext = authContext;
        this.tenantRepository = tenantRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.templateRepository = templateRepository;
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.crmAutomationService = crmAutomationService;
        this.copyBuilder = copyBuilder;
        this.evolutionClient = evolutionClient;
        this.whatsAppConnectionService = whatsAppConnectionService;
        this.mailSenderService = mailSenderService;
        this.outreachSettingsService = outreachSettingsService;
        this.outreachBotQueueService = outreachBotQueueService;
        this.mockAiEnabled = mockAiEnabled;
    }

    public List<TemplateResponse> listTemplates() {
        return templateRepository.findByTenantIdOrderByNameAsc(authContext.tenantId())
            .stream()
            .map(DtoMapper::toTemplate)
            .toList();
    }

    public List<CampaignResponse> listCampaigns() {
        return campaignRepository.findByTenantIdOrderByCreatedAtDesc(authContext.tenantId())
            .stream()
            .map(DtoMapper::toCampaign)
            .toList();
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse campaignDetail(UUID id) {
        OutreachCampaign campaign = requireCampaign(id);
        List<OutreachMessage> rows = messageRepository.findCampaignMessages(id, authContext.tenantId());
        List<CampaignMessageDetail> messages = rows.stream().map(this::toCampaignMessage).toList();
        return new CampaignDetailResponse(
            campaign.getId(), campaign.getName(), campaign.getChannel(), campaign.getStatus(), campaign.getCreatedAt(),
            campaign.getFollowUpBody(), rows.size(), count(rows, (short) 1, "QUEUED_BOT", "PENDING", "THROTTLED"),
            count(rows, (short) 1, "SENT", "WAITING_REPLY", "REPLIED"), count(rows, (short) 1, "WAITING_REPLY"),
            count(rows, (short) 1, "REPLIED"), count(rows, (short) 2, "AWAITING_APPROVAL"),
            count(rows, (short) 2, "QUEUED_BOT", "THROTTLED"), count(rows, (short) 2, "SENT"),
            count(rows, null, "FAILED"), count(rows, null, "SKIPPED"), messages
        );
    }

    @Transactional
    public CampaignDetailResponse updateCampaign(UUID id, UpdateCampaignRequest request) {
        OutreachCampaign campaign = requireCampaign(id);
        if (request.name() != null && !request.name().isBlank()) campaign.setName(request.name().trim());
        if (request.followUpBody() != null) {
            if (request.followUpBody().isBlank() || request.followUpBody().length() > 2000)
                throw new ResponseStatusException(BAD_REQUEST, "A etapa 2 deve ter entre 1 e 2.000 caracteres");
            campaign.setFollowUpBody(request.followUpBody().trim());
        }
        campaignRepository.save(campaign);
        return campaignDetail(id);
    }

    @Transactional
    public CampaignMessageDetail updateCampaignMessage(UUID campaignId, UUID messageId, UpdateCampaignMessageRequest request) {
        OutreachMessage message = messageRepository.findByIdAndTenantId(messageId, authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mensagem não encontrada"));
        if (!message.getCampaign().getId().equals(campaignId)) throw new ResponseStatusException(NOT_FOUND, "Mensagem não pertence à campanha");
        if (!isEditable(message)) throw new ResponseStatusException(BAD_REQUEST, "Mensagem já enviada e preservada no histórico");
        if (request.body() == null || request.body().isBlank() || request.body().length() > 2000)
            throw new ResponseStatusException(BAD_REQUEST, "A mensagem deve ter entre 1 e 2.000 caracteres");
        message.setBody(request.body().trim());
        if (request.subject() != null) message.setSubject(request.subject().trim());
        messageRepository.save(message);
        outreachBotQueueService.updateQueuedText(message);
        return toCampaignMessage(message);
    }

    @Transactional
    public CampaignMessageDetail retryCampaignMessage(UUID campaignId, UUID messageId) {
        OutreachMessage message = messageRepository.findByIdAndTenantId(messageId, authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mensagem não encontrada"));
        if (!message.getCampaign().getId().equals(campaignId) || !isRetryable(message))
            throw new ResponseStatusException(BAD_REQUEST, "Esta mensagem não pode ser reenfileirada");
        message.setStatus("QUEUED_BOT");
        message.setErrorDetail(null);
        messageRepository.save(message);
        outreachBotQueueService.requeue(message);
        return toCampaignMessage(message);
    }

    @Transactional
    public void setCampaignStatus(UUID id, String status) {
        OutreachCampaign campaign = requireCampaign(id);
        campaign.setStatus(status);
        campaignRepository.save(campaign);
    }

    private OutreachCampaign requireCampaign(UUID id) {
        return campaignRepository.findByIdAndTenantId(id, authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Campanha não encontrada"));
    }

    private CampaignMessageDetail toCampaignMessage(OutreachMessage m) {
        Company company = m.getLead().getCompany();
        CompanyContact contact = contactRepository.findByCompanyIdOrderByConfidenceDesc(company.getId()).stream().findFirst().orElse(null);
        return new CampaignMessageDetail(m.getId(), company.getId(), companyDisplayName(company), company.getCnpj(), company.getCity(), company.getState(),
            contact != null ? contact.getFullName() : null, contact != null ? contact.getRoleTitle() : null, m.getRecipient(),
            contact != null && contact.getEmail() != null ? contact.getEmail() : company.getEmail(), m.getOutreachStep(), m.getChannel(),
            m.getStatus(), m.getSubject(), m.getBody(), m.getCreatedAt(), m.getSentAt(), m.getRepliedAt(), m.getApprovalApprovedAt(),
            m.getProviderMessageId(), m.getErrorDetail(), isEditable(m), isRetryable(m));
    }

    private static long count(List<OutreachMessage> rows, Short step, String... statuses) {
        var allowed = java.util.Set.of(statuses);
        return rows.stream().filter(m -> step == null || m.getOutreachStep() == step).filter(m -> allowed.contains(m.getStatus())).count();
    }
    private static boolean isEditable(OutreachMessage m) { return java.util.Set.of("PENDING", "QUEUED_BOT", "THROTTLED", "AWAITING_APPROVAL", "FAILED", "SKIPPED").contains(m.getStatus()); }
    private static boolean isRetryable(OutreachMessage m) { return "WHATSAPP".equals(m.getChannel()) && java.util.Set.of("FAILED", "SKIPPED", "THROTTLED").contains(m.getStatus()); }

    @Transactional(readOnly = true)
    public OutreachReportResponse report() {
        UUID tenantId = authContext.tenantId();
        return new OutreachReportResponse(
            messageRepository.countByTenantAndStepAndStatusIn(tenantId, (short) 1,
                List.of("SENT", "WAITING_REPLY", "REPLIED")),
            messageRepository.countRepliesByTenant(tenantId),
            messageRepository.countByTenantAndStepAndStatus(tenantId, (short) 2, "AWAITING_APPROVAL"),
            messageRepository.countByTenantAndStepAndStatus(tenantId, (short) 2, "SENT"),
            messageRepository.countByTenantAndStepAndStatus(tenantId, (short) 2, "FAILED")
        );
    }

    @Transactional(readOnly = true)
    public List<FollowUpReviewItem> followUpsAwaitingApproval() {
        return messageRepository.findFollowUpsAwaitingApproval(authContext.tenantId()).stream()
            .map(m -> new FollowUpReviewItem(
                m.getId(), m.getLead().getCompany().getId(), companyDisplayName(m.getLead().getCompany()),
                m.getRecipient(), m.getBody(), m.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public FollowUpApprovalResponse approveFollowUp(UUID id) {
        OutreachMessage message = messageRepository.findByIdAndTenantId(id, authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mensagem não encontrada"));
        if (message.getOutreachStep() != 2 || !"AWAITING_APPROVAL".equals(message.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Mensagem não está aguardando aprovação");
        }
        message.setStatus("QUEUED_BOT");
        message.setApprovalApprovedAt(Instant.now());
        messageRepository.save(message);
        outreachBotQueueService.enqueueApprovedStep2(message);
        return new FollowUpApprovalResponse(message.getId(), "QUEUED_BOT", null);
    }

    @Transactional(readOnly = true)
    public List<ApproachStatusResponse> approachStatus(List<UUID> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return List.of();
        }
        UUID tenantId = authContext.tenantId();
        Map<UUID, Lead> leads = leadRepository.findByTenantIdAndCompanyIdIn(tenantId, companyIds).stream()
            .collect(java.util.stream.Collectors.toMap(lead -> lead.getCompany().getId(), lead -> lead));
        List<UUID> leadIds = leads.values().stream().map(Lead::getId).toList();
        Map<UUID, OutreachMessage> latestSent = leadIds.isEmpty() ? Map.of() :
            messageRepository.findByLeadIdInWithCampaignOrderByCreatedAtDesc(leadIds).stream()
                .filter(message -> "SENT".equals(message.getStatus()))
                .collect(java.util.stream.Collectors.toMap(
                    message -> message.getLead().getId(),
                    message -> message,
                    (first, ignored) -> first
                ));

        return companyIds.stream().distinct().map(companyId -> {
            Lead lead = leads.get(companyId);
            OutreachMessage message = lead == null ? null : latestSent.get(lead.getId());
            return new ApproachStatusResponse(
                companyId,
                lead != null ? lead.getId() : null,
                lead != null ? lead.getStatus() : null,
                message != null,
                message != null ? message.getChannel() : null,
                message != null ? message.getProvider() : null,
                message != null ? message.getRecipient() : null,
                message != null ? message.getSentAt() : null,
                message != null ? message.getErrorDetail() : null,
                message != null && isFallback(message),
                message != null ? message.getCampaign().getName() : null
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<OutreachMessageHistoryItem> companyMessages(UUID companyId) {
        Lead lead = leadRepository.findByTenantIdAndCompanyId(authContext.tenantId(), companyId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Empresa nao possui historico neste tenant"));
        return messageRepository.findByLeadIdWithCampaignOrderByCreatedAtDesc(lead.getId()).stream()
            .map(message -> new OutreachMessageHistoryItem(
                message.getId(), message.getCampaign().getId(), message.getCampaign().getName(),
                message.getChannel(), message.getProvider(), message.getRecipient(), message.getStatus(),
                message.getSubject(), preview(message.getBody()), message.getSentAt(), message.getCreatedAt(),
                message.getErrorDetail(), isFallback(message)
            ))
            .toList();
    }

    public AiCopyResponse generateCopy(AiCopyRequest request) {
        Company company = companyRepository.findById(request.companyId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Empresa não encontrada"));

        String contactName = resolveGreetingName(resolveBestContact(company.getId()));

        String companyName = company.getTradeName() != null ? company.getTradeName() : company.getLegalName();
        String cityState = (company.getCity() != null ? company.getCity() : "")
            + (company.getState() != null ? "/" + company.getState() : "");
        String segment = company.getCnaeDescription() != null ? company.getCnaeDescription() : company.getCnaeMain();
        var brand = outreachSettingsService.resolveBrandForCurrentTenant();
        boolean whatsapp = "WHATSAPP".equalsIgnoreCase(request.channel());
        var selected = ProspectCopyBuilder.ApproachId.from(request.tone());

        var approaches = copyBuilder.listApproaches(
            companyName, contactName, cityState, segment, brand, request.channel()
        ).stream()
            .map(a -> {
                String body = whatsapp
                    ? ProspectCopyBuilder.normalizeWhatsAppCopy(a.body())
                    : a.body();
                String greeting = whatsapp
                    ? ProspectCopyBuilder.normalizeWhatsAppCopy(a.greeting())
                    : a.greeting();
                return new AiCopyResponse.ApproachVariant(
                    a.id(),
                    a.label(),
                    a.description(),
                    greeting,
                    body,
                    a.subject()
                );
            })
            .toList();

        var chosen = approaches.stream()
            .filter(a -> selected.name().equals(a.id()))
            .findFirst()
            .orElse(approaches.get(0));

        String fullBody = joinGreetingBody(chosen.greeting(), chosen.body());
        if (whatsapp) {
            fullBody = ProspectCopyBuilder.normalizeWhatsAppCopy(fullBody);
        }

        return new AiCopyResponse(
            chosen.subject(),
            fullBody,
            whatsapp ? "WHATSAPP" : "EMAIL",
            chosen.greeting(),
            chosen.body(),
            chosen.id(),
            approaches
        );
    }

    private static String joinGreetingBody(String greeting, String body) {
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

    private static String companyDisplayName(Company company) {
        if (company.getTradeName() != null && !company.getTradeName().isBlank()) {
            return company.getTradeName();
        }
        return company.getLegalName();
    }

    public BulkCampaignResponse sendBulk(BulkOutreachRequest request) {
        if (request.companyIds() == null || request.companyIds().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Nenhuma empresa selecionada");
        }

        UUID tenantId = authContext.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant não encontrado"));

        OutreachTemplate template = templateRepository.findById(request.templateId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Template não encontrado"));

        String channel = request.channel() != null ? request.channel().trim().toUpperCase() : "WHATSAPP";
        boolean whatsapp = "WHATSAPP".equals(channel);
        if (whatsapp && (request.followUpBody() == null || request.followUpBody().isBlank())) {
            throw new ResponseStatusException(BAD_REQUEST, "Defina a mensagem da etapa 2 antes de iniciar o disparo.");
        }
        if (request.companyIds().stream().distinct().count() > 400) {
            throw new ResponseStatusException(BAD_REQUEST, "Uma campanha pode envolver no máximo 400 empresas.");
        }
        if (request.followUpBody() != null && request.followUpBody().length() > 2000) {
            throw new ResponseStatusException(BAD_REQUEST, "A mensagem da etapa 2 deve ter no máximo 2.000 caracteres.");
        }
        String waInstance = null;
        if (whatsapp) {
            if (!evolutionClient.isEnabled()) {
                throw new ResponseStatusException(BAD_REQUEST, "Evolution API desabilitada. Não é possível enviar WhatsApp.");
            }
            waInstance = whatsAppConnectionService.resolveSendInstance();
            var status = evolutionClient.connectionStatus(waInstance);
            if (!status.connected()) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    "WhatsApp desconectado. Conecte em Conexões → WhatsApp e tente novamente."
                );
            }
        } else if (!mailSenderService.isConfigured()) {
            throw new ResponseStatusException(BAD_REQUEST, "SMTP não configurado para envio de e-mail.");
        }

        OutreachCampaign campaign = createCampaign(tenant, request.campaignName(), channel, template, request.followUpBody());
        var brand = outreachSettingsService.resolveBrand(tenant.getId());

        int sent = 0;
        int queued = 0;
        int waSent = 0;
        int emailSent = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        List<String> nonWhatsApp = new ArrayList<>();
        List<DeliveryItem> deliveries = new ArrayList<>();
        Map<String, BulkOutreachRequest.MessageOverride> overrides =
            request.messages() != null ? request.messages() : Map.of();

        // Fallback e-mail é a regra padrão quando o canal é WhatsApp.
        boolean emailFallback = request.emailFallbackEnabled();

        for (UUID companyId : request.companyIds()) {
            Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Empresa não encontrada"));

            Lead lead = leadRepository.findByTenantIdAndCompanyId(tenantId, companyId)
                .orElseGet(() -> createLead(tenant, company));

            CompanyContact contact = resolveBestContact(companyId);

            String companyName = company.getTradeName() != null ? company.getTradeName() : company.getLegalName();
            String contactName = resolveGreetingName(contact);
            String cityState = (company.getCity() != null ? company.getCity() : "")
                + (company.getState() != null ? "/" + company.getState() : "");
            String segment = company.getCnaeDescription() != null ? company.getCnaeDescription() : company.getCnaeMain();

            BulkOutreachRequest.MessageOverride override = overrides.get(companyId.toString());
            String body = resolveBody(
                override, template, companyName, contactName, company, whatsapp, cityState, segment, brand, request
            );
            String subject = resolveSubject(override, template, companyName, brand, request, contactName, cityState, segment);

            OutreachMessage message = new OutreachMessage();
            message.setCampaign(campaign);
            message.setLead(lead);
            message.setChannel(channel);
            message.setSubject(subject);
            message.setBody(body);
            message.setCreatedAt(Instant.now());

            try {
                if (whatsapp) {
                    String phone = resolvePhone(company, contact);
                    boolean hasPhone = phone != null && !phone.isBlank();

                    if (!hasPhone) {
                        if (!emailFallback) {
                            throw new IllegalStateException("Sem telefone/WhatsApp cadastrado para este lead");
                        }
                        Delivery emailDelivery = sendEmail(company, contact, companyName, contactName, cityState, segment, subject, brand);
                        if (!emailDelivery.success()) {
                            throw new IllegalStateException(
                                emailDelivery.error() != null
                                    ? "Sem telefone e e-mail falhou: " + emailDelivery.error()
                                    : "Sem telefone e sem e-mail para fallback"
                            );
                        }
                        markEmailFallback(message, emailDelivery, "Sem telefone; enviado por e-mail");
                        messageRepository.save(message);
                        crmAutomationService.onMessageSent(lead, companyName);
                        deliveries.add(deliveryItem(companyId, companyName, message, "Fallback: sem telefone; enviado por e-mail"));
                        emailSent++;
                        sent++;
                        continue;
                    }

                    String step2Text = body;
                    message.setChannel("WHATSAPP");
                    message.setRecipient(EvolutionClient.cleanPhone(phone));
                    message.setBody(copyBuilder.whatsappStep1(companyName));
                    message.setStatus("QUEUED_BOT");
                    messageRepository.save(message);
                    outreachBotQueueService.enqueue(message, companyName, step2Text);
                    deliveries.add(deliveryItem(companyId, companyName, message, "Abertura curta na fila protegida"));
                    queued++;
                    if (false) { // Mantido apenas até remover o legado; não há caminho de execução direta.
                    Delivery delivery = sendWhatsApp(waInstance, company, contact, body, brand);
                    if (delivery.notWhatsApp()) {
                        String cleanPhone = delivery.recipient() != null ? delivery.recipient() : EvolutionClient.cleanPhone(phone);
                        nonWhatsApp.add(companyName + ": " + cleanPhone);
                    }

                    if (!delivery.success() && emailFallback) {
                        Delivery emailDelivery = sendEmail(company, contact, companyName, contactName, cityState, segment, subject, brand);
                        if (emailDelivery.success()) {
                            String reason = delivery.notWhatsApp()
                                ? "Número sem WhatsApp; enviado por e-mail"
                                : "WhatsApp falhou (" + delivery.error() + "); enviado por e-mail";
                            markEmailFallback(message, emailDelivery, reason);
                            messageRepository.save(message);
                            crmAutomationService.onMessageSent(lead, companyName);
                            deliveries.add(deliveryItem(companyId, companyName, message, "Fallback: " + reason));
                            emailSent++;
                            sent++;
                            continue;
                        }
                        if (delivery.notWhatsApp()) {
                            throw new IllegalStateException(
                                emailDelivery.error() != null
                                    ? "Número sem WhatsApp e e-mail falhou: " + emailDelivery.error()
                                    : "Número sem WhatsApp e sem e-mail para fallback"
                            );
                        }
                    }
                    if (!delivery.success()) {
                        throw new IllegalStateException(delivery.error() != null ? delivery.error() : "Falha no WhatsApp");
                    }
                    message.setChannel("WHATSAPP");
                    message.setRecipient(delivery.recipient());
                    message.setProvider("evolution");
                    message.setProviderMessageId(delivery.messageId());
                    message.setStatus("SENT");
                    message.setSentAt(Instant.now());
                    messageRepository.save(message);
                    crmAutomationService.onMessageSent(lead, companyName);
                    deliveries.add(deliveryItem(companyId, companyName, message, "Enviado no WhatsApp"));
                    waSent++;
                    sent++;
                    }
                } else {
                    Delivery delivery = sendEmail(company, contact, companyName, contactName, cityState, segment, subject, brand);
                    if (!delivery.success()) {
                        throw new IllegalStateException(delivery.error() != null ? delivery.error() : "Falha no e-mail");
                    }
                    message.setChannel("EMAIL");
                    message.setRecipient(delivery.recipient());
                    message.setProvider("smtp");
                    message.setProviderMessageId(delivery.messageId());
                    message.setStatus("SENT");
                    message.setSentAt(Instant.now());
                    messageRepository.save(message);
                    crmAutomationService.onMessageSent(lead, companyName);
                    deliveries.add(deliveryItem(companyId, companyName, message, "Enviado por e-mail"));
                    emailSent++;
                    sent++;
                }
            } catch (Exception ex) {
                failed++;
                String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                failures.add(companyName + ": " + reason);
                message.setStatus("FAILED");
                message.setErrorDetail(reason);
                messageRepository.save(message);
                deliveries.add(deliveryItem(companyId, companyName, message, reason));
                log.warn("Falha ao enviar para {}: {}", companyName, reason);
            }
        }

        campaign.setSentCount(sent);
        campaign.setStatus(failed == 0 ? (queued > 0 ? "QUEUED" : "SENT") : (sent == 0 && queued == 0 ? "FAILED" : "PARTIAL"));
        campaignRepository.save(campaign);
        tenant.setCreditsUsed(tenant.getCreditsUsed() + sent);
        tenantRepository.save(tenant);

        String detail = buildDetail(queued, waSent, emailSent, failed, failures, nonWhatsApp);
        return new BulkCampaignResponse(
            campaign.getId(),
            campaign.getName(),
            campaign.getChannel(),
            campaign.getStatus(),
            sent,
            waSent,
            emailSent,
            failed,
            detail,
            campaign.getCreatedAt(),
            List.copyOf(nonWhatsApp),
            List.copyOf(deliveries)
        );
    }

    private static DeliveryItem deliveryItem(UUID companyId, String companyName, OutreachMessage message, String detail) {
        return new DeliveryItem(companyId, companyName, message.getStatus(), message.getChannel(), message.getProvider(),
            message.getRecipient(), isFallback(message), detail);
    }

    private static boolean isFallback(OutreachMessage message) {
        return "smtp-fallback".equalsIgnoreCase(message.getProvider());
    }

    private static String preview(String body) {
        if (body == null) return null;
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 220 ? compact : compact.substring(0, 217) + "...";
    }

    private void markEmailFallback(OutreachMessage message, Delivery emailDelivery, String reason) {
        message.setChannel("EMAIL");
        message.setRecipient(emailDelivery.recipient());
        message.setProvider("smtp-fallback");
        message.setProviderMessageId(emailDelivery.messageId());
        message.setStatus("SENT");
        message.setSentAt(Instant.now());
        message.setErrorDetail(reason);
    }

    @Transactional
    protected OutreachCampaign createCampaign(
        Tenant tenant,
        String name,
        String channel,
        OutreachTemplate template,
        String followUpBody
    ) {
        OutreachCampaign campaign = new OutreachCampaign();
        campaign.setTenant(tenant);
        campaign.setName(name != null && !name.isBlank() ? name : "Campanha");
        campaign.setChannel(channel);
        campaign.setTemplate(template);
        campaign.setStatus("SENDING");
        campaign.setSentCount(0);
        campaign.setFollowUpBody(followUpBody != null ? followUpBody.trim() : null);
        campaign.setCreatedAt(Instant.now());
        return campaignRepository.save(campaign);
    }

    private Delivery sendWhatsApp(
        String instance,
        Company company,
        CompanyContact contact,
        String body,
        OutreachSettingsService.BrandProfile brand
    ) {
        String phone = resolvePhone(company, contact);
        if (phone == null || phone.isBlank()) {
            return Delivery.fail(null, "Sem telefone/WhatsApp cadastrado para este lead");
        }
        String clean = EvolutionClient.cleanPhone(phone);
        if (!evolutionClient.isWhatsAppNumber(instance, clean)) {
            return Delivery.notWhatsApp(clean);
        }
        String outbound = ProspectCopyBuilder.normalizeWhatsAppCopy(body);
        var logo = copyBuilder.resolveWhatsAppLogo(brand);
        if (logo.isPresent()) {
            var asset = logo.get();
            var media = evolutionClient.sendBrandedImageCaption(instance, clean, outbound, asset.base64(), asset.fileName());
            if (media.success()) {
                return Delivery.ok(clean, media.messageId());
            }
            if (isNotWhatsAppError(media.error())) {
                return Delivery.notWhatsApp(clean);
            }
            log.warn("Mídia WA falhou ({}), tentando texto", media.error());
        }
        var text = evolutionClient.sendText(instance, clean, outbound);
        if (text.success()) {
            return Delivery.ok(clean, text.messageId());
        }
        if (isNotWhatsAppError(text.error())) {
            return Delivery.notWhatsApp(clean);
        }
        return Delivery.fail(clean, text.error());
    }

    private Delivery sendEmail(
        Company company,
        CompanyContact contact,
        String companyName,
        String contactName,
        String cityState,
        String segment,
        String subject,
        OutreachSettingsService.BrandProfile brand
    ) {
        String email = resolveEmail(company, contact);
        if (email == null || email.isBlank()) {
            return Delivery.fail(null, "Sem e-mail cadastrado para este lead");
        }
        String safeSubject = subject != null && !subject.isBlank()
            ? subject
            : copyBuilder.emailSubject(companyName, brand);
        String html = copyBuilder.emailHtml(companyName, contactName, cityState, segment, brand);
        String text = copyBuilder.emailText(companyName, contactName, cityState, segment, brand);
        var result = mailSenderService.sendHtml(
            email,
            safeSubject,
            html,
            text,
            email,
            brand != null ? brand.senderName() : null,
            toInlineImage(copyBuilder.resolveEmailInlineLogo(brand))
        );
        if (result.success()) {
            return Delivery.ok(result.deliveredTo() != null ? result.deliveredTo() : email, null);
        }
        return Delivery.fail(email, result.error());
    }

    private String resolveBody(
        BulkOutreachRequest.MessageOverride override,
        OutreachTemplate template,
        String companyName,
        String contactName,
        Company company,
        boolean whatsapp,
        String cityState,
        String segment,
        OutreachSettingsService.BrandProfile brand,
        BulkOutreachRequest request
    ) {
        String senderName = brand != null && brand.senderName() != null && !brand.senderName().isBlank()
            ? brand.senderName()
            : authContext.currentUser().fullName();
        // Override só vale se ainda tiver placeholders. Texto já resolvido (prévia de 1 empresa)
        // NÃO pode ser reaproveitado no lote: gerava "Olá Telefone" / empresa errada em todos.
        if (override != null && override.body() != null && !override.body().isBlank()
            && hasCopyPlaceholders(override.body())) {
            String resolved = override.body()
                .replace("{{companyName}}", companyName)
                .replace("{{contactName}}", contactName)
                .replace("{{cnaeDescription}}", company.getCnaeDescription() != null ? company.getCnaeDescription() : "")
                .replace("{{senderName}}", senderName);
            return whatsapp ? ProspectCopyBuilder.normalizeWhatsAppCopy(resolved) : resolved;
        }

        var approach = ProspectCopyBuilder.ApproachId.from(request != null ? request.approachId() : null);
        String editable = request != null ? request.editableBody() : null;
        if (editable != null && !editable.isBlank()) {
            var built = copyBuilder.buildApproach(
                approach, whatsapp, companyName, contactName, cityState, segment, brand
            );
            String joined = joinGreetingBody(built.greeting(), editable.trim());
            return whatsapp ? ProspectCopyBuilder.normalizeWhatsAppCopy(joined) : joined;
        }

        if (whatsapp) {
            return copyBuilder.whatsappCaption(companyName, contactName, cityState, segment, brand, approach);
        }
        if (template.getBodyTemplate() != null && !template.getBodyTemplate().isBlank()) {
            return template.getBodyTemplate()
                .replace("{{companyName}}", companyName)
                .replace("{{cnaeDescription}}", company.getCnaeDescription() != null ? company.getCnaeDescription() : "")
                .replace("{{contactName}}", contactName)
                .replace("{{senderName}}", senderName);
        }
        return copyBuilder.emailText(companyName, contactName, cityState, segment, brand, approach);
    }

    private CompanyContact resolveBestContact(UUID companyId) {
        var contacts = contactRepository.findByCompanyIdOrderByConfidenceDesc(companyId);
        return contacts.stream()
            .filter(c -> c.getFullName() != null && !ProspectCopyBuilder.isNonPersonContactLabel(c.getFullName()))
            .findFirst()
            .orElseGet(() -> contacts.stream().findFirst().orElse(null));
    }

    private static String resolveGreetingName(CompanyContact contact) {
        if (contact == null || contact.getFullName() == null || contact.getFullName().isBlank()) {
            return "decisor";
        }
        if (ProspectCopyBuilder.isNonPersonContactLabel(contact.getFullName())) {
            return "decisor";
        }
        return contact.getFullName().trim();
    }

    private static boolean hasCopyPlaceholders(String text) {
        return text.contains("{{companyName}}")
            || text.contains("{{contactName}}")
            || text.contains("{{cnaeDescription}}")
            || text.contains("{{senderName}}");
    }

    private String resolveSubject(
        BulkOutreachRequest.MessageOverride override,
        OutreachTemplate template,
        String companyName,
        OutreachSettingsService.BrandProfile brand,
        BulkOutreachRequest request,
        String contactName,
        String cityState,
        String segment
    ) {
        if (override != null && override.subject() != null && !override.subject().isBlank()
            && hasCopyPlaceholders(override.subject())) {
            return override.subject().replace("{{companyName}}", companyName);
        }
        String editableSubject = request != null ? request.editableSubject() : null;
        if (editableSubject != null && !editableSubject.isBlank() && !hasCopyPlaceholders(editableSubject)) {
            // Assunto editado na prévia: usa no lote (não depende do nome da empresa no meio).
            return editableSubject.trim();
        }
        var approach = ProspectCopyBuilder.ApproachId.from(request != null ? request.approachId() : null);
        if (template.getSubject() != null && !template.getSubject().isBlank()
            && (request == null || request.approachId() == null || request.approachId().isBlank())) {
            return template.getSubject().replace("{{companyName}}", companyName);
        }
        return copyBuilder.emailSubject(companyName, brand, approach);
    }

    private static String resolvePhone(Company company, CompanyContact contact) {
        if (contact != null) {
            if (contact.getWhatsapp() != null && !contact.getWhatsapp().isBlank()) {
                return contact.getWhatsapp();
            }
            if (contact.getPhone() != null && !contact.getPhone().isBlank()) {
                return contact.getPhone();
            }
        }
        return company.getPhone();
    }

    private static String resolveEmail(Company company, CompanyContact contact) {
        if (contact != null && contact.getEmail() != null && !contact.getEmail().isBlank()) {
            return contact.getEmail();
        }
        return company.getEmail();
    }

    private static String buildDetail(
        int queued,
        int waSent,
        int emailSent,
        int failed,
        List<String> failures,
        List<String> nonWhatsApp
    ) {
        int sent = waSent + emailSent;
        String breakdown = waSent + " WhatsApp · " + emailSent + " e-mail";
        StringBuilder detail = new StringBuilder();
        if (failed == 0 && sent > 0) {
            detail.append(sent).append(" mensagem(ns) entregue(s): ").append(breakdown).append('.');
        } else if (failed > 0) {
            String sample = failures.stream().limit(3).reduce((a, b) -> a + " | " + b).orElse("");
            detail.append(sent).append(" entregue(s) (").append(breakdown).append("), ")
                .append(failed).append(" falha(s). ").append(sample);
        }
        if (queued > 0) {
            detail.append(' ').append(queued).append(" abertura(s) na fila protegida do WhatsApp.");
        }
        if (!nonWhatsApp.isEmpty()) {
            detail.append(' ')
                .append(nonWhatsApp.size())
                .append(" número(s) sem WhatsApp (ver relatório).");
        }
        return detail.toString().trim();
    }

    private static boolean isNotWhatsAppError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String lower = error.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("sem whatsapp")
            || lower.contains("\"exists\":false")
            || lower.contains("\"exists\": false");
    }

    private Lead createLead(Tenant tenant, Company company) {
        Lead lead = new Lead();
        lead.setTenant(tenant);
        lead.setCompany(company);
        lead.setStatus("NEW");
        lead.setScore(50);
        lead.setCreatedAt(Instant.now());
        lead.setUpdatedAt(Instant.now());
        return leadRepository.save(lead);
    }

    private record Delivery(boolean success, String recipient, String messageId, String error, boolean notWhatsApp) {
        static Delivery ok(String recipient, String messageId) {
            return new Delivery(true, recipient, messageId, null, false);
        }

        static Delivery fail(String recipient, String error) {
            return new Delivery(false, recipient, null, error, false);
        }

        static Delivery notWhatsApp(String phone) {
            return new Delivery(false, phone, null, "Número sem WhatsApp (não encontrado na rede)", true);
        }
    }

    private static MailSenderService.InlineImage toInlineImage(
        java.util.Optional<ProspectCopyBuilder.EmailInlineLogo> logo
    ) {
        return logo
            .map(l -> MailSenderService.InlineImage.from(l.contentId(), l.bytes(), l.mimeType(), l.fileName()))
            .orElse(null);
    }
}
