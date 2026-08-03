package com.prospectportal.web.controller;

import com.prospectportal.module.outreach.OutreachService;
import com.prospectportal.module.prospect.ProspectAutomationService;
import com.prospectportal.web.dto.AiCopyRequest;
import com.prospectportal.web.dto.AiCopyResponse;
import com.prospectportal.web.dto.BulkCampaignResponse;
import com.prospectportal.web.dto.BulkOutreachRequest;
import com.prospectportal.web.dto.CampaignResponse;
import com.prospectportal.web.dto.ChannelStatusResponse;
import com.prospectportal.web.dto.TemplateResponse;
import com.prospectportal.web.dto.TestEmailResponse;
import com.prospectportal.web.dto.TestWhatsAppRequest;
import com.prospectportal.web.dto.TestWhatsAppResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outreach")
public class OutreachController {

    private final OutreachService outreachService;
    private final ProspectAutomationService prospectAutomationService;

    public OutreachController(
        OutreachService outreachService,
        ProspectAutomationService prospectAutomationService
    ) {
        this.outreachService = outreachService;
        this.prospectAutomationService = prospectAutomationService;
    }

    @GetMapping("/templates")
    public List<TemplateResponse> templates() {
        return outreachService.listTemplates();
    }

    @GetMapping("/campaigns")
    public List<CampaignResponse> campaigns() {
        return outreachService.listCampaigns();
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

    @PostMapping("/test-email")
    public TestEmailResponse testEmail() {
        return prospectAutomationService.sendTestEmail();
    }

    @PostMapping("/test-whatsapp")
    public TestWhatsAppResponse testWhatsApp(@RequestBody(required = false) TestWhatsAppRequest request) {
        String phone = request != null ? request.phone() : null;
        return prospectAutomationService.sendTestWhatsApp(phone);
    }
}
