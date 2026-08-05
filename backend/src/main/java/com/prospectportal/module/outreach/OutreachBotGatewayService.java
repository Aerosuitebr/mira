package com.prospectportal.module.outreach;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Proxy interno: a UI nunca acessa diretamente o outreach-bot. */
@Service
public class OutreachBotGatewayService {
    private final RestClient client;
    private final String serviceToken;

    public OutreachBotGatewayService(
            @Value("${app.outreach.bot-url:http://mira-outreach-bot:8090}") String botUrl,
            @Value("${app.outreach.bot-service-token:}") String serviceToken) {
        this.client = RestClient.builder().baseUrl(botUrl).build();
        this.serviceToken = serviceToken;
    }

    public Map<String, Object> status() {
        return requestGet("/v1/status");
    }

    public Map<String, Object> pause() {
        return requestPost("/v1/pause");
    }

    public Map<String, Object> resume() {
        return requestPost("/v1/resume");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestGet(String path) {
        return client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestPost(String path) {
        return client.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .retrieve().body(Map.class);
    }
}
