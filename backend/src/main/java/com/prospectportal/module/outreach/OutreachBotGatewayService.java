package com.prospectportal.module.outreach;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

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

    public Map<String, Object> status(String instance) {
        return requestGetWithInstance("/v1/status", instance);
    }

    public Map<String, Object> quota(String instance) {
        return requestGetWithInstance("/v1/quota", instance);
    }

    public Map<String, Object> pause() {
        return requestPost("/v1/pause");
    }

    public Map<String, Object> resume() {
        return requestPost("/v1/resume");
    }

    public Map<String, Object> pauseCampaign(UUID id) { return requestPost("/v1/campaigns/" + id + "/pause"); }
    public Map<String, Object> resumeCampaign(UUID id) { return requestPost("/v1/campaigns/" + id + "/resume"); }
    public Map<String, Object> cancelCampaign(UUID id) { return requestPost("/v1/campaigns/" + id + "/cancel"); }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestGet(String path) {
        return client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestGetWithInstance(String path, String instance) {
        return client.get()
            .uri(builder -> builder.path(path).queryParam("instance", instance).build())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestPost(String path) {
        return client.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
            .retrieve().body(Map.class);
    }
}
