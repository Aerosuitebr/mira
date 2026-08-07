package com.prospectportal.web.controller;

import com.prospectportal.module.outreach.OutreachService;
import com.prospectportal.module.outreach.OutreachBotGatewayService;
import com.prospectportal.module.prospect.ProspectAutomationService;
import com.prospectportal.module.whatsapp.WhatsAppConnectionService;
import com.prospectportal.web.dto.AiCopyRequest;
import com.prospectportal.web.dto.AiCopyResponse;
import com.prospectportal.web.dto.BulkCampaignResponse;
import com.prospectportal.web.dto.BulkOutreachRequest;
import com.prospectportal.web.dto.CampaignResponse;
import com.prospectportal.web.dto.ChannelStatusResponse;
import com.prospectportal.web.dto.TemplateResponse;
import com.prospectportal.web.dto.TestEmailRequest;
import com.prospectportal.web.dto.TestEmailResponse;
import com.prospectportal.web.dto.TestWhatsAppRequest;
import com.prospectportal.web.dto.TestWhatsAppResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import com.prospectportal.web.dto.OutreachMessageHistoryItem;
import com.prospectportal.web.dto.OutreachReportResponse;
import com.prospectportal.web.dto.FollowUpReviewItem;
import com.prospectportal.web.dto.FollowUpApprovalResponse;
import com.prospectportal.web.dto.CampaignDetailResponse;
import com.prospectportal.web.dto.CampaignMessageDetail;
import com.prospectportal.web.dto.UpdateCampaignMessageRequest;
import com.prospectportal.web.dto.UpdateCampaignRequest;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/outreach")
public class OutreachController {

    private final OutreachService outreachService;
    private final ProspectAutomationService prospectAutomationService;
    private final OutreachBotGatewayService outreachBotGatewayService;
    private final WhatsAppConnectionService whatsAppConnectionService;

    public OutreachController(
        OutreachService outreachService,
        ProspectAutomationService prospectAutomationService,
        OutreachBotGatewayService outreachBotGatewayService,
        WhatsAppConnectionService whatsAppConnectionService
    ) {
        this.outreachService = outreachService;
        this.prospectAutomationService = prospectAutomationService;
        this.outreachBotGatewayService = outreachBotGatewayService;
        this.whatsAppConnectionService = whatsAppConnectionService;
    }

    @GetMapping("/templates")
    public List<TemplateResponse> templates() {
        return outreachService.listTemplates();
    }

    @GetMapping("/campaigns")
    public List<CampaignResponse> campaigns() {
        return outreachService.listCampaigns();
    }

    @GetMapping("/campaigns/{id}")
    public CampaignDetailResponse campaign(@PathVariable UUID id) { return outreachService.campaignDetail(id); }

    @PutMapping("/campaigns/{id}")
    public CampaignDetailResponse updateCampaign(@PathVariable UUID id, @RequestBody UpdateCampaignRequest request) {
        return outreachService.updateCampaign(id, request);
    }

    @PutMapping("/campaigns/{campaignId}/messages/{messageId}")
    public CampaignMessageDetail updateMessage(@PathVariable UUID campaignId, @PathVariable UUID messageId,
                                                @RequestBody UpdateCampaignMessageRequest request) {
        return outreachService.updateCampaignMessage(campaignId, messageId, request);
    }

    @PostMapping("/campaigns/{campaignId}/messages/{messageId}/retry")
    public CampaignMessageDetail retryMessage(@PathVariable UUID campaignId, @PathVariable UUID messageId) {
        return outreachService.retryCampaignMessage(campaignId, messageId);
    }

    @PostMapping("/campaigns/{campaignId}/messages/retry-problems")
    public CampaignDetailResponse retryProblems(@PathVariable UUID campaignId) {
        return outreachService.retryCampaignProblems(campaignId);
    }

    @PostMapping("/campaigns/{id}/pause")
    public CampaignDetailResponse pauseCampaign(@PathVariable UUID id) {
        outreachBotGatewayService.pauseCampaign(id);
        outreachService.setCampaignStatus(id, "PAUSED");
        return outreachService.campaignDetail(id);
    }

    @PostMapping("/campaigns/{id}/resume")
    public CampaignDetailResponse resumeCampaign(@PathVariable UUID id) {
        outreachBotGatewayService.resumeCampaign(id);
        outreachService.setCampaignStatus(id, "SENDING");
        return outreachService.campaignDetail(id);
    }

    @PostMapping("/campaigns/{id}/cancel")
    public CampaignDetailResponse cancelCampaign(@PathVariable UUID id) {
        outreachBotGatewayService.cancelCampaign(id);
        outreachService.cancelCampaign(id);
        return outreachService.campaignDetail(id);
    }

    @GetMapping("/report")
    public OutreachReportResponse report() {
        return outreachService.report();
    }

    @GetMapping("/follow-ups")
    public List<FollowUpReviewItem> followUpsAwaitingApproval() {
        return outreachService.followUpsAwaitingApproval();
    }

    @PostMapping("/follow-ups/{id}/approve")
    public FollowUpApprovalResponse approveFollowUp(@PathVariable UUID id) {
        return outreachService.approveFollowUp(id);
    }

    @GetMapping("/companies/{companyId}/messages")
    public List<OutreachMessageHistoryItem> companyMessages(@PathVariable UUID companyId) {
        return outreachService.companyMessages(companyId);
    }

    @PostMapping("/ai-copy")
    public AiCopyResponse generateCopy(@RequestBody AiCopyRequest request) {
        return outreachService.generateCopy(request);
    }

    @PostMapping("/campaigns/bulk")
    public BulkCampaignResponse sendBulk(@RequestBody BulkOutreachRequest request) {
        return outreachService.sendBulk(request);
    }

    @GetMapping("/channels")
    public ChannelStatusResponse channels() {
        return prospectAutomationService.channels();
    }

    @GetMapping("/bot/status")
    public Map<String, Object> botStatus() {
        return outreachBotGatewayService.status(whatsAppConnectionService.resolveSendInstance());
    }

    @GetMapping("/bot/quota")
    public Map<String, Object> botQuota() {
        return outreachBotGatewayService.quota(whatsAppConnectionService.resolveSendInstance());
    }

    @PostMapping("/bot/pause")
    public Map<String, Object> pauseBot() {
        return outreachBotGatewayService.pause();
    }

    @PostMapping("/bot/resume")
    public Map<String, Object> resumeBot() {
        return outreachBotGatewayService.resume();
    }

    @PostMapping("/test-email")
    public TestEmailResponse testEmail(@RequestBody(required = false) TestEmailRequest request) {
        String email = request != null ? request.email() : null;
        return prospectAutomationService.sendTestEmail(email);
    }

    @PostMapping("/test-whatsapp")
    public TestWhatsAppResponse testWhatsApp(@RequestBody(required = false) TestWhatsAppRequest request) {
        String phone = request != null ? request.phone() : null;
        return prospectAutomationService.sendTestWhatsApp(phone);
    }
}
