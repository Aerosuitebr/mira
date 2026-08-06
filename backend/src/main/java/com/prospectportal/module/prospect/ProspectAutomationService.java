package com.prospectportal.module.prospect;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.crm.repository.LeadRepository;
import com.prospectportal.module.discovery.DiscoveryService;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.enrichment.EnrichmentService;
import com.prospectportal.module.enrichment.entity.CompanyContact;
import com.prospectportal.module.enrichment.repository.CompanyContactRepository;
import com.prospectportal.module.evolution.EvolutionClient;
import com.prospectportal.module.mail.MailSenderService;
import com.prospectportal.module.outreach.CrmAutomationService;
import com.prospectportal.module.outreach.OutreachSettingsService;
import com.prospectportal.module.outreach.OutreachBotQueueService;
import com.prospectportal.module.outreach.entity.OutreachCampaign;
import com.prospectportal.module.outreach.entity.OutreachMessage;
import com.prospectportal.module.outreach.repository.OutreachCampaignRepository;
import com.prospectportal.module.outreach.repository.OutreachMessageRepository;
import com.prospectportal.module.prospect.entity.ProspectJob;
import com.prospectportal.module.prospect.repository.ProspectJobRepository;
import com.prospectportal.module.whatsapp.WhatsAppConnectionService;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.ChannelStatusResponse;
import com.prospectportal.web.dto.CompanyResponse;
import com.prospectportal.web.dto.PageResponse;
import com.prospectportal.web.dto.ProspectJobRequest;
import com.prospectportal.web.dto.ProspectJobResponse;
import com.prospectportal.web.dto.TestEmailResponse;
import com.prospectportal.web.dto.TestWhatsAppResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProspectAutomationService {

    private static final Logger log = LoggerFactory.getLogger(ProspectAutomationService.class);

    private final AuthContext authContext;
    private final TenantRepository tenantRepository;
    private final ProspectJobRepository jobRepository;
    private final OutreachCampaignRepository campaignRepository;
    private final OutreachMessageRepository messageRepository;
    private final CompanyRepository companyRepository;
    private final CompanyContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final DiscoveryService discoveryService;
    private final EnrichmentService enrichmentService;
    private final CrmAutomationService crmAutomationService;
    private final EvolutionClient evolutionClient;
    private final WhatsAppConnectionService whatsAppConnectionService;
    private final MailSenderService mailSenderService;
    private final ProspectCopyBuilder copyBuilder;
    private final OutreachSettingsService outreachSettingsService;
    private final OutreachBotQueueService outreachBotQueueService;
    private final WhatsAppThrottle throttle;
    private final ProspectJobPreparer jobPreparer;
    private final boolean defaultTestMode;
    private final boolean whatsappEnabled;
    private final boolean dispatchEnabled;

    public ProspectAutomationService(
        AuthContext authContext,
        TenantRepository tenantRepository,
        ProspectJobRepository jobRepository,
        OutreachCampaignRepository campaignRepository,
        OutreachMessageRepository messageRepository,
        CompanyRepository companyRepository,
        CompanyContactRepository contactRepository,
        LeadRepository leadRepository,
        DiscoveryService discoveryService,
        EnrichmentService enrichmentService,
        CrmAutomationService crmAutomationService,
        EvolutionClient evolutionClient,
        WhatsAppConnectionService whatsAppConnectionService,
        MailSenderService mailSenderService,
        ProspectCopyBuilder copyBuilder,
        OutreachSettingsService outreachSettingsService,
        OutreachBotQueueService outreachBotQueueService,
        WhatsAppThrottle throttle,
        @Lazy ProspectJobPreparer jobPreparer,
        @Value("${app.outreach.test-mode:false}") boolean defaultTestMode,
        @Value("${app.outreach.whatsapp.enabled:true}") boolean whatsappEnabled,
        @Value("${app.outreach.dispatch-enabled:false}") boolean dispatchEnabled
    ) {
        this.authContext = authContext;
        this.tenantRepository = tenantRepository;
        this.jobRepository = jobRepository;
        this.campaignRepository = campaignRepository;
        this.messageRepository = messageRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.discoveryService = discoveryService;
        this.enrichmentService = enrichmentService;
        this.crmAutomationService = crmAutomationService;
        this.evolutionClient = evolutionClient;
        this.whatsAppConnectionService = whatsAppConnectionService;
        this.mailSenderService = mailSenderService;
        this.copyBuilder = copyBuilder;
        this.outreachSettingsService = outreachSettingsService;
        this.outreachBotQueueService = outreachBotQueueService;
        this.throttle = throttle;
        this.jobPreparer = jobPreparer;
        this.defaultTestMode = defaultTestMode;
        this.whatsappEnabled = whatsappEnabled;
        this.dispatchEnabled = dispatchEnabled;
    }

    public ChannelStatusResponse channels() {
        String instance = safeTenantInstance();
        var wa = evolutionClient.connectionStatus(instance);
        boolean emailOk = mailSenderService.isConfigured();
        return new ChannelStatusResponse(
            emailOk,
            mailSenderService.isTestMode(),
            mailSenderService.testEmail(),
            emailOk
                ? (mailSenderService.isTestMode()
                    ? "E-mail SMTP pronto (modo teste → " + mailSenderService.testEmail() + ")"
                    : "E-mail SMTP conectado")
                : "E-mail SMTP não configurado",
            evolutionClient.isEnabled() && whatsappEnabled,
            wa.connected(),
            wa.label(),
            instance,
            wa.rawState()
        );
    }

    public TestEmailResponse sendTestEmail() {
        return sendTestEmail(null);
    }

    /**
     * Amostra de e-mail com o mesmo builder da campanha.
     * Se {@code destinationOverride} for informado, envia para esse endereço mesmo em produção
     * (validação pontual do padrão). Caso contrário, exige modo teste + APP_OUTREACH_TEST_EMAIL.
     */
    public TestEmailResponse sendTestEmail(String destinationOverride) {
        String destination;
        if (destinationOverride != null && !destinationOverride.isBlank()) {
            destination = destinationOverride.trim();
        } else if (mailSenderService.isTestMode()) {
            destination = mailSenderService.testEmail();
            if (destination == null || destination.isBlank()) {
                return TestEmailResponse.fail("APP_OUTREACH_TEST_EMAIL não configurado");
            }
        } else {
            return TestEmailResponse.fail(
                "Informe o e-mail de destino no body {\"email\":\"...\"} para amostra pontual, "
                    + "ou ative APP_OUTREACH_TEST_MODE com APP_OUTREACH_TEST_EMAIL."
            );
        }

        // Pessoa fictícia neutra (não usar nome real da equipe) para validar saudação.
        String company = "Helipse Aviation Ltda";
        String contact = "Maria Silva";
        var brand = outreachSettingsService.resolveBrandForCurrentTenant();
        String subject = copyBuilder.emailSubject(company, brand) + " (amostra de padrão)";
        String html = copyBuilder.emailHtml(company, contact, "Rio de Janeiro/RJ", "Manutenção de aeronaves", brand);
        String text = copyBuilder.emailText(company, contact, "Rio de Janeiro/RJ", "Manutenção de aeronaves", brand);
        var result = mailSenderService.sendHtml(
            destination,
            subject,
            html,
            text,
            destination,
            brand.senderName(),
            toInlineImage(copyBuilder.resolveEmailInlineLogo(brand))
        );
        if (!result.success()) {
            return TestEmailResponse.fail(result.error());
        }
        return TestEmailResponse.ok(result.deliveredTo(), subject);
    }

    public TestWhatsAppResponse sendTestWhatsApp(String phone) {
        String instance = safeTenantInstance();
        if (!dispatchEnabled) {
            return TestWhatsAppResponse.fail(
                phone == null ? "" : EvolutionClient.cleanPhone(phone),
                instance,
                "Envios WhatsApp estão desabilitados na Fase 1. A fila e as mensagens ficam disponíveis para revisão, sem disparo."
            );
        }
        if (phone == null || phone.isBlank()) {
            return TestWhatsAppResponse.fail(
                "",
                instance,
                "Informe o telefone de destino. Não há número mockado: use um celular real para teste pontual."
            );
        }
        String destination = phone;
        String clean = EvolutionClient.cleanPhone(destination);

        if (!evolutionClient.isEnabled()) {
            return TestWhatsAppResponse.fail(clean, instance, "Evolution API desabilitada no MIRA");
        }

        var status = evolutionClient.connectionStatus(instance);
        if (!status.connected()) {
            var qr = evolutionClient.fetchConnectQrBase64(instance).orElse(null);
            return TestWhatsAppResponse.needsQr(
                clean,
                instance,
                "WhatsApp desconectado (" + status.label() + "). Conecte em Conexões → WhatsApp (QR Code) e tente novamente.",
                qr
            );
        }

        var brand = outreachSettingsService.resolveBrandForCurrentTenant();
        String caption = copyBuilder.whatsappCaption(
            "Helipse Aviation Ltda",
            "Maria Silva",
            "Rio de Janeiro/RJ",
            "Manutenção e reparação de aeronaves",
            brand
        );
        String plain = copyBuilder.whatsappFallbackPlainText(
            "Helipse Aviation Ltda",
            "Maria Silva",
            "Rio de Janeiro/RJ",
            "Manutenção e reparação de aeronaves",
            brand
        );
        var send = sendBrandedWhatsApp(instance, clean, caption, plain, brand);
        if (!send.success()) {
            return TestWhatsAppResponse.fail(clean, instance, send.error());
        }
        return TestWhatsAppResponse.ok(clean, instance, send.mode(), send.messageId(), caption);
    }

    private BrandedSend sendBrandedWhatsApp(
        String instance,
        String cleanPhone,
        String caption,
        String fallbackPlain,
        OutreachSettingsService.BrandProfile brand
    ) {
        var logo = copyBuilder.resolveWhatsAppLogo(brand);
        if (logo.isPresent()) {
            var asset = logo.get();
            var result = evolutionClient.sendBrandedImageCaption(
                instance,
                cleanPhone,
                caption,
                asset.base64(),
                asset.fileName()
            );
            if (result.success()) {
                return new BrandedSend(true, "logo+caption", result.messageId(), null);
            }
            log.warn("Envio com logo falhou ({}), tentando texto", result.error());
        }
        var textResult = evolutionClient.sendText(instance, cleanPhone, fallbackPlain);
        if (textResult.success()) {
            return new BrandedSend(true, "plain-text", textResult.messageId(), null);
        }
        return new BrandedSend(false, "failed", null, textResult.error());
    }

    private String safeTenantInstance() {
        try {
            return whatsAppConnectionService.resolveSendInstance();
        } catch (Exception ex) {
            return evolutionClient.instanceName();
        }
    }

    private record BrandedSend(boolean success, String mode, String messageId, String error) {}

    @Transactional
    public ProspectJobResponse start(ProspectJobRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request inválido");
        }
        String campaignName = blankToNull(request.name());
        if (campaignName == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe um nome para a campanha.");
        }
        if (campaignName.length() > 120) {
            throw new ResponseStatusException(BAD_REQUEST, "O nome da campanha deve ter no máximo 120 caracteres.");
        }
        UUID tenantId = authContext.tenantId();
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant não encontrado"));

        List<UUID> selectedCompanyIds = request.selectedCompanyIds() == null
            ? List.of()
            : request.selectedCompanyIds().stream().distinct().limit(400).toList();
        if (!selectedCompanyIds.isEmpty()) {
            var approached = new java.util.HashSet<>(
                messageRepository.findCompanyIdsWithFirstStepAttempt(tenantId, selectedCompanyIds)
            );
            selectedCompanyIds = selectedCompanyIds.stream()
                .filter(companyId -> !approached.contains(companyId))
                .toList();
            if (selectedCompanyIds.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "As empresas selecionadas já possuem abordagem registrada. Consulte a campanha existente para evitar contato duplicado.");
            }
        }
        int limit = !selectedCompanyIds.isEmpty()
            ? selectedCompanyIds.size()
            : request.companyLimit() != null ? Math.min(Math.max(request.companyLimit(), 1), 400) : 20;
        boolean testMode = request.testMode() != null ? request.testMode() : defaultTestMode;
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        String openingMessage = blankToNull(request.openingMessage());
        String followUpBody = blankToNull(request.followUpBody());
        if (openingMessage != null && openingMessage.length() > 500) {
            throw new ResponseStatusException(BAD_REQUEST, "A primeira mensagem deve ter no máximo 500 caracteres.");
        }
        if (openingMessage != null && openingMessage.toLowerCase().matches("(?s).*(https?://|www\\.).*")) {
            throw new ResponseStatusException(BAD_REQUEST, "A primeira mensagem não pode conter links.");
        }
        if (followUpBody == null || followUpBody.length() > 2000) {
            throw new ResponseStatusException(BAD_REQUEST, "A segunda mensagem deve ter entre 1 e 2.000 caracteres.");
        }

        OutreachCampaign campaign = new OutreachCampaign();
        campaign.setTenant(tenant);
        campaign.setName(campaignName);
        campaign.setChannel("AUTO");
        campaign.setStatus("QUEUED");
        campaign.setSentCount(0);
        campaign.setFollowUpBody(followUpBody);
        campaign.setCreatedAt(Instant.now());
        campaign = campaignRepository.save(campaign);

        ProspectJob job = new ProspectJob();
        job.setTenant(tenant);
        job.setCampaign(campaign);
        job.setName(campaign.getName());
        job.setCnae(blankToNull(request.cnae()));
        job.setState(blankToNull(request.state()));
        job.setCity(blankToNull(request.city()));
        job.setKeyword(blankToNull(request.keyword()));
        job.setSelectedCompanyIds(selectedCompanyIds.isEmpty()
            ? null
            : selectedCompanyIds.stream().map(UUID::toString).reduce((left, right) -> left + "," + right).orElse(null));
        job.setCompanyLimit(limit);
        job.setStatus("QUEUED");
        job.setTestMode(testMode);
        job.setDryRun(dryRun);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job = jobRepository.save(job);

        // Uma seleção explícita do usuário precisa ficar pronta antes de a UI
        // confirmar a fila. Isso evita jobs presos em QUEUED sem contagem.
        if (!selectedCompanyIds.isEmpty()) {
            prepareJob(job.getId(), openingMessage);
            ProspectJob prepared = jobRepository.findById(job.getId()).orElse(job);
            return toResponse(prepared);
        }

        jobPreparer.prepareAsync(job.getId());
        return toResponse(job);
    }

    @Transactional
    public void markFailed(UUID jobId, String detail) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("FAILED");
            job.setErrorDetail(detail);
            job.setUpdatedAt(Instant.now());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            if (job.getCampaign() != null) {
                job.getCampaign().setStatus("FAILED");
                campaignRepository.save(job.getCampaign());
            }
        });
    }

    @Transactional
    public void prepareJob(UUID jobId) {
        prepareJob(jobId, null);
    }

    private void prepareJob(UUID jobId, String openingMessage) {
        ProspectJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalStateException("Job não encontrado: " + jobId));
        tenantRepository.findByIdForUpdate(job.getTenant().getId())
            .orElseThrow(() -> new IllegalStateException("Tenant não encontrado para o job: " + jobId));

        // Fase 1 termina com a campanha preparada para revisão. O worker de envio
        // permanece bloqueado por configuração até a etapa de webhook/resposta.
        job.setStatus("QUEUED");
        job.setStartedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setNextDispatchAt(Instant.now());
        jobRepository.save(job);

        if (job.getCampaign() != null) {
            job.getCampaign().setStatus("QUEUED");
            campaignRepository.save(job.getCampaign());
        }

        List<UUID> companyIds = selectedCompanyIds(job);
        if (companyIds.isEmpty()) {
            PageResponse<CompanyResponse> page = discoveryService.search(
                job.getKeyword(),
                job.getCnae(),
                job.getState(),
                job.getCity(),
                null,
                true,
                false,
                0,
                job.getCompanyLimit()
            );
            companyIds = page.content().stream().map(CompanyResponse::id).limit(job.getCompanyLimit()).toList();
        }
        if (!companyIds.isEmpty()) {
            var approached = new java.util.HashSet<>(
                messageRepository.findCompanyIdsWithFirstStepAttempt(job.getTenant().getId(), companyIds)
            );
            companyIds = companyIds.stream().filter(companyId -> !approached.contains(companyId)).toList();
        }
        job.setFoundCount(companyIds.size());
        jobRepository.save(job);

        if (companyIds.isEmpty()) {
            completeJob(job, "Nenhuma empresa encontrada para o segmento/região.");
            return;
        }

        enrichmentService.enrichCompanies(companyIds, false);
        job.setEnrichedCount(companyIds.size());
        jobRepository.save(job);

        Tenant tenant = job.getTenant();
        OutreachCampaign campaign = job.getCampaign();
        int queued = 0;

        for (UUID companyId : companyIds) {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company == null) {
                continue;
            }
            Lead lead = leadRepository.findByTenantIdAndCompanyId(tenant.getId(), companyId)
                .orElseGet(() -> createLead(tenant, company));

            CompanyContact contact = contactRepository.findByCompanyIdOrderByConfidenceDesc(companyId)
                .stream().findFirst().orElse(null);

            String companyName = displayName(company);
            String contactName = greetingName(contact);
            String cityState = formatCityState(company);
            String segment = company.getCnaeDescription() != null ? company.getCnaeDescription() : company.getCnaeMain();
            var brand = outreachSettingsService.resolveBrand(tenant.getId());
            String recipient = EvolutionClient.cleanPhone(preferredPhone(contact, company));
            if (!recipient.isBlank() && messageRepository.existsFirstStepAttemptToRecipient(tenant.getId(), recipient)) {
                log.info("Empresa {} ignorada: destinatário já possui abordagem registrada", companyName);
                continue;
            }

            OutreachMessage message = new OutreachMessage();
            message.setCampaign(campaign);
            message.setLead(lead);
            message.setChannel("AUTO");
            message.setSubject(copyBuilder.emailSubject(companyName, brand));
            message.setBody(copyBuilder.whatsappStep1(companyName, openingMessage));
            message.setStatus("QUEUED_BOT");
            message.setProspectJobId(job.getId());
            message.setRecipient(recipient);
            message.setCreatedAt(Instant.now());
            messageRepository.save(message);
            String followUp = campaign.getFollowUpBody() == null
                ? copyBuilder.whatsappFollowUp(companyName, brand)
                : campaign.getFollowUpBody().replace("{{empresa}}", companyName);
            outreachBotQueueService.enqueue(message, companyName, followUp);
            queued++;
        }

        job.setQueuedCount(queued);
        job.setUpdatedAt(Instant.now());
        if (queued == 0) {
            completeJob(job, "Nenhuma mensagem enfileirada.");
        } else {
            jobRepository.save(job);
        }
        log.info("Job {} preparado: {} empresas, {} mensagens na fila", jobId, companyIds.size(), queued);
    }

    private List<UUID> selectedCompanyIds(ProspectJob job) {
        if (job.getSelectedCompanyIds() == null || job.getSelectedCompanyIds().isBlank()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String value : job.getSelectedCompanyIds().split(",")) {
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Ignora ids inválidos de jobs antigos, sem interromper a fila.
            }
        }
        return ids;
    }

    @Transactional
    public void dispatchNext(ProspectJob job) {
        if (!"RUNNING".equals(job.getStatus())) {
            return;
        }
        if (job.getNextDispatchAt() != null && job.getNextDispatchAt().isAfter(Instant.now())) {
            return;
        }

        List<OutreachMessage> pending = messageRepository.findPendingByJobId(job.getId());
        if (pending.isEmpty()) {
            completeJob(job, null);
            return;
        }

        OutreachMessage message = pending.getFirst();
        message.setStatus("SENDING");
        messageRepository.save(message);

        Lead lead = message.getLead();
        Company company = lead.getCompany();
        CompanyContact contact = contactRepository.findByCompanyIdOrderByConfidenceDesc(company.getId())
            .stream().findFirst().orElse(null);

        String companyName = displayName(company);
        String contactName = greetingName(contact);
        String cityState = formatCityState(company);
        String segment = company.getCnaeDescription() != null ? company.getCnaeDescription() : company.getCnaeMain();
        String phone = preferredPhone(contact, company);
        String email = preferredEmail(contact, company);
        var brand = outreachSettingsService.resolveBrand(job.getTenant().getId());

        boolean sent = false;

        if (job.isDryRun()) {
            message.setStatus("SKIPPED");
            message.setErrorDetail("Dry-run - envio não realizado");
            message.setSentAt(Instant.now());
            messageRepository.save(message);
            job.setFailedCount(job.getFailedCount() + 1);
            scheduleNext(job);
            maybeComplete(job);
            return;
        }

        boolean tryWa = whatsappEnabled
            && evolutionClient.isEnabled()
            && (job.getWaPausedUntil() == null || job.getWaPausedUntil().isBefore(Instant.now()))
            && phone != null
            && throttle.canSendWhatsApp();

        if (tryWa) {
            String clean = EvolutionClient.cleanPhone(phone);
            String instance = whatsAppConnectionService.resolveSendInstanceForTenant(job.getTenant().getId());
            boolean onWa = evolutionClient.isWhatsAppNumber(instance, clean);
            if (onWa) {
                String body = copyBuilder.whatsappCaption(companyName, contactName, cityState, segment, brand);
                if (job.isTestMode()) {
                    // Em modo teste não dispara WA real em massa: cai no e-mail de teste
                    message.setErrorDetail("Modo teste: WhatsApp omitido; fallback e-mail");
                } else {
                    String caption = copyBuilder.whatsappCaption(companyName, contactName, cityState, segment, brand);
                    String plain = copyBuilder.whatsappFallbackPlainText(companyName, contactName, cityState, segment, brand);
                    var branded = sendBrandedWhatsApp(instance, clean, caption, plain, brand);
                    if (branded.success()) {
                        message.setChannel("WHATSAPP");
                        message.setBody(caption);
                        message.setRecipient(clean);
                        message.setProvider("evolution");
                        message.setProviderMessageId(branded.messageId());
                        message.setStatus("SENT");
                        message.setSentAt(Instant.now());
                        messageRepository.save(message);
                        job.setWaSent(job.getWaSent() + 1);
                        bumpCampaign(job);
                        crmAutomationService.onMessageSent(lead, companyName);
                        scheduleNext(job);
                        maybeComplete(job);
                        return;
                    }
                    if (branded.error() != null && branded.error().toLowerCase().contains("rate")) {
                        job.setWaPausedUntil(Instant.now().plusSeconds(3600));
                        message.setErrorDetail("WA rate-limited: " + branded.error());
                    } else {
                        message.setErrorDetail("WA falhou: " + branded.error());
                    }
                }
            } else {
                message.setErrorDetail("Telefone sem WhatsApp");
            }
        } else if (phone != null && !throttle.canSendWhatsApp()) {
            message.setErrorDetail(throttle.blockReason());
        }

        // Fallback e-mail: em modo teste envia mesmo sem e-mail do lead (vai para test-email)
        String emailTarget = email;
        if ((emailTarget == null || emailTarget.isBlank()) && job.isTestMode()) {
            emailTarget = mailSenderService.testEmail();
        }
        if (emailTarget == null || emailTarget.isBlank()) {
            message.setStatus("FAILED");
            message.setChannel("EMAIL");
            message.setErrorDetail(
                (message.getErrorDetail() != null ? message.getErrorDetail() + " · " : "")
                    + "Sem e-mail para fallback"
            );
            messageRepository.save(message);
            job.setFailedCount(job.getFailedCount() + 1);
            scheduleNext(job);
            maybeComplete(job);
            return;
        }

        String subject = copyBuilder.emailSubject(companyName, brand);
        String html = copyBuilder.emailHtml(companyName, contactName, cityState, segment, brand);
        String text = copyBuilder.emailText(companyName, contactName, cityState, segment, brand);
        var mailResult = mailSenderService.sendHtml(
            emailTarget,
            subject,
            html,
            text,
            email != null ? email : emailTarget,
            brand.senderName(),
            toInlineImage(copyBuilder.resolveEmailInlineLogo(brand))
        );
        if (mailResult.success()) {
            OutreachMessage emailMsg = message;
            if (message.getErrorDetail() != null) {
                // marca original como falha WA e cria fallback
                message.setStatus("FAILED");
                message.setChannel("WHATSAPP");
                messageRepository.save(message);

                emailMsg = new OutreachMessage();
                emailMsg.setCampaign(job.getCampaign());
                emailMsg.setLead(lead);
                emailMsg.setFallbackOf(message.getId());
                emailMsg.setProspectJobId(job.getId());
                emailMsg.setCreatedAt(Instant.now());
            }
            emailMsg.setChannel("EMAIL");
            emailMsg.setSubject(subject);
            emailMsg.setBody(text);
            emailMsg.setRecipient(mailResult.deliveredTo());
            emailMsg.setProvider("smtp");
            emailMsg.setStatus("SENT");
            emailMsg.setSentAt(Instant.now());
            emailMsg.setErrorDetail(null);
            messageRepository.save(emailMsg);
            job.setEmailSent(job.getEmailSent() + 1);
            bumpCampaign(job);
            crmAutomationService.onMessageSent(lead, companyName);
            sent = true;
        } else {
            message.setStatus("FAILED");
            message.setChannel("EMAIL");
            message.setErrorDetail(
                (message.getErrorDetail() != null ? message.getErrorDetail() + " · " : "")
                    + "E-mail falhou: " + mailResult.error()
            );
            messageRepository.save(message);
            job.setFailedCount(job.getFailedCount() + 1);
        }

        if (sent || !sent) {
            scheduleNext(job);
            maybeComplete(job);
        }
    }

    public List<ProspectJobResponse> listJobs() {
        return jobRepository.findByTenantIdOrderByCreatedAtDesc(authContext.tenantId())
            .stream().map(this::toResponse).toList();
    }

    public ProspectJobResponse getJob(UUID id) {
        return jobRepository.findByIdAndTenantId(id, authContext.tenantId())
            .map(this::toResponse)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job não encontrado"));
    }

    @Transactional
    public ProspectJobResponse pause(UUID id) {
        ProspectJob job = requireJob(id);
        if (!"RUNNING".equals(job.getStatus()) && !"QUEUED".equals(job.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Job não está em execução");
        }
        job.setStatus("PAUSED");
        job.setUpdatedAt(Instant.now());
        if (job.getCampaign() != null) {
            job.getCampaign().setStatus("PAUSED");
            campaignRepository.save(job.getCampaign());
        }
        return toResponse(jobRepository.save(job));
    }

    @Transactional
    public ProspectJobResponse resume(UUID id) {
        ProspectJob job = requireJob(id);
        if (!"PAUSED".equals(job.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Job não está pausado");
        }
        job.setStatus("RUNNING");
        job.setNextDispatchAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        if (job.getCampaign() != null) {
            job.getCampaign().setStatus("RUNNING");
            campaignRepository.save(job.getCampaign());
        }
        return toResponse(jobRepository.save(job));
    }

    private ProspectJob requireJob(UUID id) {
        return jobRepository.findByIdAndTenantId(id, authContext.tenantId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job não encontrado"));
    }

    private void scheduleNext(ProspectJob job) {
        if (!throttle.withinBusinessHours()) {
            job.setNextDispatchAt(throttle.nextBusinessWindow());
        } else {
            job.setNextDispatchAt(throttle.nextSlotAfterSend());
        }
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
    }

    private void maybeComplete(ProspectJob job) {
        long pending = messageRepository.findPendingByJobId(job.getId()).size();
        // SENDING leftover shouldn't block if we already processed
        if (pending == 0) {
            long stillSending = messageRepository.findPendingByJobId(job.getId()).size();
            if (stillSending == 0) {
                completeJob(job, null);
            }
        }
    }

    private void completeJob(ProspectJob job, String detail) {
        job.setStatus("COMPLETED");
        if (detail != null) {
            job.setErrorDetail(detail);
        }
        job.setCompletedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        if (job.getCampaign() != null) {
            job.getCampaign().setStatus("COMPLETED");
            campaignRepository.save(job.getCampaign());
        }
    }

    private void bumpCampaign(ProspectJob job) {
        if (job.getCampaign() != null) {
            OutreachCampaign campaign = job.getCampaign();
            campaign.setSentCount(campaign.getSentCount() + 1);
            campaignRepository.save(campaign);
        }
        Tenant tenant = job.getTenant();
        tenant.setCreditsUsed(tenant.getCreditsUsed() + 1);
        tenantRepository.save(tenant);
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

    private static String preferredPhone(CompanyContact contact, Company company) {
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

    private static String preferredEmail(CompanyContact contact, Company company) {
        if (contact != null && contact.getEmail() != null && !contact.getEmail().isBlank()) {
            return contact.getEmail();
        }
        return company.getEmail();
    }

    private static String displayName(Company company) {
        if (company.getTradeName() != null && !company.getTradeName().isBlank()) {
            return company.getTradeName();
        }
        return company.getLegalName();
    }

    private static String greetingName(CompanyContact contact) {
        if (contact == null || contact.getFullName() == null || contact.getFullName().isBlank()) {
            return "decisor";
        }
        if (ProspectCopyBuilder.isNonPersonContactLabel(contact.getFullName())) {
            return "decisor";
        }
        return contact.getFullName().trim();
    }

    private static String formatCityState(Company company) {
        String city = company.getCity() != null ? company.getCity() : "";
        String state = company.getState() != null ? company.getState() : "";
        if (!city.isBlank() && !state.isBlank()) {
            return city + "/" + state;
        }
        return (city + " " + state).trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ProspectJobResponse toResponse(ProspectJob job) {
        return new ProspectJobResponse(
            job.getId(),
            job.getName(),
            job.getCnae(),
            job.getState(),
            job.getCity(),
            job.getKeyword(),
            job.getCompanyLimit(),
            job.getStatus(),
            job.isTestMode(),
            job.isDryRun(),
            job.getFoundCount(),
            job.getEnrichedCount(),
            job.getQueuedCount(),
            job.getWaSent(),
            job.getEmailSent(),
            job.getFailedCount(),
            job.getErrorDetail(),
            job.getNextDispatchAt(),
            job.getWaPausedUntil(),
            job.getCreatedAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getCampaign() != null ? job.getCampaign().getId() : null
        );
    }

    private static MailSenderService.InlineImage toInlineImage(
        java.util.Optional<ProspectCopyBuilder.EmailInlineLogo> logo
    ) {
        return logo
            .map(l -> MailSenderService.InlineImage.from(l.contentId(), l.bytes(), l.mimeType(), l.fileName()))
            .orElse(null);
    }
}
