package com.prospectportal.module.enrichment;

import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.enrichment.entity.CompanyContact;
import com.prospectportal.module.enrichment.repository.CompanyContactRepository;
import com.prospectportal.module.enrichment.web.WebEnrichmentSignals;
import com.prospectportal.module.enrichment.web.WebContactabilityService;
import com.prospectportal.module.enrichment.web.WebPresenceEnricher;
import com.prospectportal.module.enrichment.registry.PublicRegistryEnricher;
import com.prospectportal.module.enrichment.registry.RegistryPartner;
import com.prospectportal.module.enrichment.registry.RegistryRefreshResult;
import com.prospectportal.web.dto.ContactResponse;
import com.prospectportal.web.mapper.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.net.URI;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    private final CompanyRepository companyRepository;
    private final CompanyContactRepository contactRepository;
    private final WebPresenceEnricher webPresenceEnricher;
    private final PublicRegistryEnricher publicRegistryEnricher;
    private final WebContactabilityService webContactabilityService;
    private final EnrichmentProperties enrichmentProperties;
    private final TransactionTemplate transactionTemplate;

    public EnrichmentService(
        CompanyRepository companyRepository,
        CompanyContactRepository contactRepository,
        WebPresenceEnricher webPresenceEnricher,
        PublicRegistryEnricher publicRegistryEnricher,
        WebContactabilityService webContactabilityService,
        EnrichmentProperties enrichmentProperties,
        PlatformTransactionManager transactionManager
    ) {
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.webPresenceEnricher = webPresenceEnricher;
        this.publicRegistryEnricher = publicRegistryEnricher;
        this.webContactabilityService = webContactabilityService;
        this.enrichmentProperties = enrichmentProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<ContactResponse> listContacts(UUID companyId) {
        return contactRepository.findByCompanyIdOrderByConfidenceDesc(companyId)
            .stream()
            .map(DtoMapper::toContact)
            .toList();
    }

    public List<ContactResponse> enrichCompanies(List<UUID> companyIds, boolean forceRefresh) {
        if (companyIds.isEmpty()) {
            return List.of();
        }

        int workers = Math.min(
            Math.max(1, enrichmentProperties.getParallelism()),
            companyIds.size()
        );
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<CompletableFuture<List<ContactResponse>>> futures = companyIds.stream()
                .map(companyId -> CompletableFuture.supplyAsync(
                    () -> enrichOneCompany(companyId, forceRefresh),
                    executor
                ))
                .toList();

            List<ContactResponse> enriched = new ArrayList<>();
            for (CompletableFuture<List<ContactResponse>> future : futures) {
                enriched.addAll(future.join());
            }
            return enriched;
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<ContactResponse> enrichOneCompany(UUID companyId, boolean forceRefresh) {
        try {
            List<ContactResponse> result = transactionTemplate.execute(status -> {
                Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Empresa não encontrada"));

                List<CompanyContact> existing = contactRepository.findByCompanyIdOrderByConfidenceDesc(companyId);
                if (!existing.isEmpty() && !forceRefresh) {
                    return existing.stream().map(DtoMapper::toContact).toList();
                }

                if (forceRefresh && !existing.isEmpty()) {
                    contactRepository.deleteByCompanyId(companyId);
                }

                List<CompanyContact> saved = buildContacts(company, forceRefresh);
                if (saved.isEmpty()) {
                    return List.<ContactResponse>of();
                }
                return saved.stream().map(DtoMapper::toContact).toList();
            });
            return result != null ? result : List.of();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Falha ao enriquecer empresa {}: {}", companyId, ex.getMessage());
            return List.of();
        }
    }

    private List<CompanyContact> buildContacts(Company company, boolean forceRefresh) {
        List<CompanyContact> contacts = new ArrayList<>();

        RegistryRefreshResult registry = publicRegistryEnricher.refresh(company, forceRefresh);
        if (registry.applied()) {
            companyRepository.save(company);
        }

        for (RegistryPartner partner : registry.partners()) {
            contacts.add(savePartnerContact(company, partner, registry.source()));
        }

        WebEnrichmentSignals web = webPresenceEnricher.enrich(company);
        webContactabilityService.applyFromEnrichment(company, web);

        if (web.getWebsiteUrl() != null) {
            String website = web.getWebsiteUrl();
            company.setWebsite(website);
            companyRepository.save(company);
            contacts.add(saveProfileContact(company, "Site da empresa", "Presença web oficial",
                website, null, null, "WEBSITE_PROFILE", (short) 92));
        } else if (company.getWebsite() != null && !company.getWebsite().isBlank()) {
            company.setWebsite(null);
            companyRepository.save(company);
        }

        int emailCount = 0;
        for (String email : prioritizeEmails(web.getEmails(), web.getWebsiteUrl())) {
            if (emailCount++ >= 3) {
                break;
            }
            contacts.add(saveContact(company, "E-mail do site", "Página de contato / rodapé", email, null, null,
                "WEBSITE_CRAWL", (short) 90));
        }

        int phoneCount = 0;
        for (String phone : web.getPhones()) {
            if (!isValidBrazilPhone(phone) || phoneCount >= 2) {
                continue;
            }
            phoneCount++;
            contacts.add(saveContact(company, "Telefone fixo", "Página de contato", null, phone, null,
                "WEBSITE_CRAWL", (short) 88));
        }

        int whatsappCount = 0;
        for (String whatsapp : web.getWhatsappPhones()) {
            if (!isValidBrazilPhone(whatsapp) || whatsappCount >= 2) {
                continue;
            }
            whatsappCount++;
            contacts.add(saveContact(company, "WhatsApp", "Canal móvel no site", null, null, whatsapp,
                "WEBSITE_CRAWL", (short) 89));
        }

        for (String address : web.getAddresses()) {
            contacts.add(saveAddressContact(company, address));
            break;
        }

        for (String linkedin : web.getLinkedinUrls()) {
            CompanyContact social = new CompanyContact();
            social.setCompany(company);
            social.setFullName("LinkedIn da empresa");
            social.setRoleTitle("Rede profissional");
            social.setLinkedinUrl(linkedin);
            social.setSource("SOCIAL_LINKEDIN");
            social.setConfidence((short) 80);
            social.setEnrichedAt(Instant.now());
            social.setCreatedAt(Instant.now());
            contacts.add(contactRepository.save(social));
        }

        for (String instagram : web.getInstagramUrls()) {
            CompanyContact social = new CompanyContact();
            social.setCompany(company);
            social.setFullName("Instagram");
            social.setRoleTitle("Rede social");
            social.setInstagramUrl(instagram);
            social.setSource("SOCIAL_INSTAGRAM");
            social.setConfidence((short) 76);
            social.setEnrichedAt(Instant.now());
            social.setCreatedAt(Instant.now());
            contacts.add(contactRepository.save(social));
        }

        if (company.getEmail() != null && !company.getEmail().isBlank()) {
            String rfRole = registryRole(registry);
            short rfConfidence = registry.applied() ? (short) 68 : (short) 55;
            contacts.add(saveContact(company, "Contato cadastral RF", rfRole,
                company.getEmail(), company.getPhone(), company.getPhone(), "RECEITA_FEDERAL", rfConfidence));
        } else if (company.getPhone() != null && !company.getPhone().isBlank()) {
            contacts.add(saveContact(company, "Telefone cadastral RF", registryRole(registry),
                null, company.getPhone(), company.getPhone(), "RECEITA_FEDERAL",
                registry.applied() ? (short) 68 : (short) 55));
        }

        String rfAddress = formatRfAddress(company);
        if (rfAddress != null) {
            contacts.add(saveRfAddressContact(company, rfAddress));
        } else if (contacts.isEmpty()) {
            contacts.add(saveContact(company, "Sem contato público", "Tente buscar no LinkedIn ou site manualmente",
                null, null, null, "MANUAL_REVIEW", (short) 20));
        }

        return contacts;
    }

    private CompanyContact savePartnerContact(Company company, RegistryPartner partner, String registrySource) {
        CompanyContact contact = new CompanyContact();
        contact.setCompany(company);
        contact.setFullName("Sócio (cadastro público)");
        contact.setRoleTitle(partner.name() + " - " + partner.role());
        contact.setSource("PUBLIC_REGISTRY");
        contact.setConfidence((short) 62);
        contact.setEnrichedAt(Instant.now());
        contact.setCreatedAt(Instant.now());
        return contactRepository.save(contact);
    }

    private String registryRole(RegistryRefreshResult registry) {
        if (!registry.applied() || registry.source() == null) {
            return "Receita Federal (base local - pode estar desatualizada)";
        }
        return switch (registry.source()) {
            case "BRASIL_API" -> "Consulta pública Brasil API (dados abertos da RF)";
            case "OPEN_CNPJ" -> "Consulta pública OpenCNPJ (dados abertos da RF)";
            default -> "Consulta pública cadastral";
        };
    }

    private CompanyContact saveContact(
        Company company,
        String fullName,
        String role,
        String email,
        String phone,
        String whatsapp,
        String source,
        short confidence
    ) {
        CompanyContact contact = new CompanyContact();
        contact.setCompany(company);
        contact.setFullName(fullName);
        contact.setRoleTitle(role);
        contact.setEmail(email);
        contact.setPhone(phone);
        contact.setWhatsapp(whatsapp);
        contact.setSource(source);
        contact.setConfidence(confidence);
        contact.setEnrichedAt(Instant.now());
        contact.setCreatedAt(Instant.now());
        return contactRepository.save(contact);
    }

    private CompanyContact saveAddressContact(Company company, String address) {
        CompanyContact contact = new CompanyContact();
        contact.setCompany(company);
        contact.setFullName("Endereço do site");
        contact.setRoleTitle(address);
        contact.setSource("WEBSITE_CRAWL");
        contact.setConfidence((short) 87);
        contact.setEnrichedAt(Instant.now());
        contact.setCreatedAt(Instant.now());
        return contactRepository.save(contact);
    }

    private CompanyContact saveRfAddressContact(Company company, String address) {
        CompanyContact contact = new CompanyContact();
        contact.setCompany(company);
        contact.setFullName("Endereço cadastral RF");
        contact.setRoleTitle(address);
        contact.setSource("RECEITA_FEDERAL");
        contact.setConfidence((short) 58);
        contact.setEnrichedAt(Instant.now());
        contact.setCreatedAt(Instant.now());
        return contactRepository.save(contact);
    }

    private String formatRfAddress(Company company) {
        String street = company.getStreet();
        if (street == null || street.isBlank()) {
            return null;
        }
        StringBuilder line = new StringBuilder(capitalizeWords(street.trim()));
        if (company.getNeighborhood() != null && !company.getNeighborhood().isBlank()) {
            line.append(" - ").append(capitalizeWords(company.getNeighborhood().trim()));
        }
        if (company.getCity() != null && !company.getCity().isBlank()) {
            line.append(" - ").append(capitalizeWords(company.getCity().trim()));
            if (company.getState() != null && !company.getState().isBlank()) {
                line.append("/").append(company.getState().trim().toUpperCase(Locale.ROOT));
            }
        }
        if (company.getZipCode() != null && !company.getZipCode().isBlank()) {
            line.append(" - CEP ").append(formatZipCode(company.getZipCode()));
        }
        return line.toString();
    }

    private String formatZipCode(String zipCode) {
        String digits = zipCode.replaceAll("\\D", "");
        if (digits.length() != 8) {
            return zipCode;
        }
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }

    private String capitalizeWords(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            String part = parts[i];
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }

    private List<String> prioritizeEmails(Set<String> emails, String websiteUrl) {
        if (emails.isEmpty()) {
            return List.of();
        }
        String host = extractHost(websiteUrl);
        List<String> sorted = new ArrayList<>(emails);
        sorted.sort((a, b) -> {
            int scoreA = emailDomainScore(a, host);
            int scoreB = emailDomainScore(b, host);
            return Integer.compare(scoreB, scoreA);
        });
        return sorted;
    }

    private int emailDomainScore(String email, String host) {
        if (host == null || host.isBlank()) {
            return 0;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT).replace("www.", "");
        if (domain.equals(normalizedHost) || domain.endsWith("." + normalizedHost) || normalizedHost.contains(domain.replace(".com.br", ""))) {
            return 10;
        }
        if (domain.endsWith(".com.br") || domain.endsWith(".aero")) {
            return 5;
        }
        if (domain.contains("gmail.com") || domain.contains("hotmail.") || domain.contains("yahoo.")) {
            return 1;
        }
        return 3;
    }

    private String extractHost(String websiteUrl) {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(websiteUrl).getHost();
        } catch (Exception ex) {
            return null;
        }
    }

    private CompanyContact saveProfileContact(
        Company company,
        String fullName,
        String role,
        String websiteUrl,
        String instagramUrl,
        String linkedinUrl,
        String source,
        short confidence
    ) {
        CompanyContact contact = new CompanyContact();
        contact.setCompany(company);
        contact.setFullName(fullName);
        contact.setRoleTitle(role);
        contact.setWebsiteUrl(websiteUrl);
        contact.setInstagramUrl(instagramUrl);
        contact.setLinkedinUrl(linkedinUrl);
        contact.setSource(source);
        contact.setConfidence(confidence);
        contact.setEnrichedAt(Instant.now());
        contact.setCreatedAt(Instant.now());
        return contactRepository.save(contact);
    }

    private boolean isValidBrazilPhone(String digits) {
        String normalized = digits.replaceAll("\\D", "");
        if (normalized.startsWith("55") && normalized.length() > 11) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() != 10 && normalized.length() != 11) {
            return false;
        }
        int ddd = Integer.parseInt(normalized.substring(0, 2));
        return ddd >= 11 && ddd <= 99;
    }
}
