package com.prospectportal.web.controller;

import com.prospectportal.module.outreach.OutreachSettingsService;
import com.prospectportal.web.dto.OutreachSettingsRequest;
import com.prospectportal.web.dto.OutreachSettingsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/outreach-settings")
public class OutreachSettingsController {

    private final OutreachSettingsService settingsService;

    public OutreachSettingsController(OutreachSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public OutreachSettingsResponse get() {
        return settingsService.get();
    }

    @PutMapping
    public OutreachSettingsResponse update(@RequestBody OutreachSettingsRequest request) {
        return settingsService.update(request);
    }

    @PostMapping("/test-approval-notification")
    public com.prospectportal.web.dto.ApprovalNotificationTestResponse testApprovalNotification() {
        return settingsService.sendApprovalNotificationTest();
    }
}
