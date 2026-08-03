package com.prospectportal.module.enrichment.web;

import java.util.LinkedHashSet;
import java.util.Set;

public class WebEnrichmentSignals {

    private String websiteUrl;
    private final Set<String> emails = new LinkedHashSet<>();
    private final Set<String> phones = new LinkedHashSet<>();
    private final Set<String> whatsappPhones = new LinkedHashSet<>();
    private final Set<String> addresses = new LinkedHashSet<>();
    private final Set<String> linkedinUrls = new LinkedHashSet<>();
    private final Set<String> instagramUrls = new LinkedHashSet<>();
    private final Set<String> facebookUrls = new LinkedHashSet<>();

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public Set<String> getEmails() {
        return emails;
    }

    public Set<String> getPhones() {
        return phones;
    }

    public Set<String> getWhatsappPhones() {
        return whatsappPhones;
    }

    public Set<String> getAddresses() {
        return addresses;
    }

    public Set<String> getLinkedinUrls() {
        return linkedinUrls;
    }

    public Set<String> getInstagramUrls() {
        return instagramUrls;
    }

    public Set<String> getFacebookUrls() {
        return facebookUrls;
    }

    public boolean hasAnyContact() {
        return !emails.isEmpty() || !phones.isEmpty() || !whatsappPhones.isEmpty()
            || !addresses.isEmpty() || !linkedinUrls.isEmpty()
            || !instagramUrls.isEmpty() || !facebookUrls.isEmpty();
    }
}
