package com.prospectportal.module.enrichment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.enrichment")
public class EnrichmentProperties {

    private boolean webEnabled = true;
    private boolean registryEnabled = true;
    private int requestTimeoutMs = 5000;
    private int rateLimitMs = 200;
    private int maxPagesPerCompany = 3;
    private int parallelism = 4;
    private String userAgent = "ProspectPortal-Enrichment/1.0 (+https://prospectportal.local)";
    private String brasilApiUrl = "https://brasilapi.com.br/api/cnpj/v1";
    private String openCnpjUrl = "https://kitana.opencnpj.com/cnpj";

    public boolean isWebEnabled() {
        return webEnabled;
    }

    public void setWebEnabled(boolean webEnabled) {
        this.webEnabled = webEnabled;
    }

    public boolean isRegistryEnabled() {
        return registryEnabled;
    }

    public void setRegistryEnabled(boolean registryEnabled) {
        this.registryEnabled = registryEnabled;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getRateLimitMs() {
        return rateLimitMs;
    }

    public void setRateLimitMs(int rateLimitMs) {
        this.rateLimitMs = rateLimitMs;
    }

    public int getMaxPagesPerCompany() {
        return maxPagesPerCompany;
    }

    public void setMaxPagesPerCompany(int maxPagesPerCompany) {
        this.maxPagesPerCompany = maxPagesPerCompany;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getBrasilApiUrl() {
        return brasilApiUrl;
    }

    public void setBrasilApiUrl(String brasilApiUrl) {
        this.brasilApiUrl = brasilApiUrl;
    }

    public String getOpenCnpjUrl() {
        return openCnpjUrl;
    }

    public void setOpenCnpjUrl(String openCnpjUrl) {
        this.openCnpjUrl = openCnpjUrl;
    }
}
