package com.prospectportal.web.controller;

import com.prospectportal.module.whatsapp.WhatsAppConnectionService;
import com.prospectportal.web.dto.WhatsAppConnectionResponse;
import com.prospectportal.web.dto.WhatsAppSendRequest;
import com.prospectportal.web.dto.WhatsAppSendResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    private final WhatsAppConnectionService connectionService;

    public WhatsAppController(WhatsAppConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/status")
    public WhatsAppConnectionResponse status() {
        return connectionService.status();
    }

    @PostMapping("/connect")
    public WhatsAppConnectionResponse connect() {
        return connectionService.connect();
    }

    @PostMapping("/qr")
    public WhatsAppConnectionResponse refreshQr() {
        return connectionService.refreshQr();
    }

    @PostMapping("/webhook")
    public WhatsAppConnectionResponse configureWebhook() {
        return connectionService.configureReplyWebhook();
    }

    @PostMapping("/disconnect")
    public WhatsAppConnectionResponse disconnect() {
        return connectionService.disconnect();
    }

    @PostMapping("/send")
    public WhatsAppSendResponse send(@RequestBody WhatsAppSendRequest request) {
        return connectionService.sendDirectMessage(
            request != null ? request.phone() : null,
            request != null ? request.message() : null
        );
    }
}
