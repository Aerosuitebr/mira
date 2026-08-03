package com.prospectportal.module.enrichment.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.enrichment.EnrichmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PublicRegistryEnricher {

    private static final Logger log = LoggerFactory.getLogger(PublicRegistryEnricher.class);
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EnrichmentProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PublicRegistryEnricher(EnrichmentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public RegistryRefreshResult refresh(Company company, boolean forceRefresh) {
        if (!properties.isRegistryEnabled()) {
            return RegistryRefreshResult.skipped();
        }

        String cnpj = normalizeCnpj(company.getCnpj());
        if (cnpj.length() != 14) {
            return RegistryRefreshResult.failed();
        }

        if (!forceRefresh && hasCompleteCadastralData(company)) {
            return RegistryRefreshResult.skipped();
        }

        JsonNode brasilApi = fetchJson(properties.getBrasilApiUrl() + "/" + cnpj);
        if (brasilApi != null && !brasilApi.isMissingNode() && !brasilApi.has("message")) {
            List<RegistryPartner> partners = applyBrasilApi(company, brasilApi);
            log.info("Cadastro de {} atualizado via Brasil API", company.getLegalName());
            return RegistryRefreshResult.success("BRASIL_API", partners);
        }

        JsonNode openCnpj = fetchJson(properties.getOpenCnpjUrl() + "/" + cnpj);
        if (openCnpj != null && openCnpj.path("success").asBoolean(false)) {
            List<RegistryPartner> partners = applyOpenCnpj(company, openCnpj.path("data"));
            log.info("Cadastro de {} atualizado via OpenCNPJ", company.getLegalName());
            return RegistryRefreshResult.success("OPEN_CNPJ", partners);
        }

        log.debug("Nenhuma API pública respondeu para CNPJ {}", cnpj);
        return RegistryRefreshResult.failed();
    }

    private boolean hasCompleteCadastralData(Company company) {
        return isPresent(company.getStreet())
            && isPresent(company.getEmail())
            && isPresent(company.getPhone())
            && isPresent(company.getZipCode());
    }

    private List<RegistryPartner> applyBrasilApi(Company company, JsonNode node) {
        setIfPresent(company::setLegalName, text(node, "razao_social"));
        setIfPresent(company::setTradeName, text(node, "nome_fantasia"));
        setIfPresent(company::setCity, text(node, "municipio"));
        setIfPresent(company::setState, text(node, "uf"));
        setIfPresent(company::setNeighborhood, text(node, "bairro"));
        setIfPresent(company::setZipCode, digits(text(node, "cep")));
        setIfPresent(company::setEmail, text(node, "email"));
        setIfPresent(company::setPhone, normalizePhone(text(node, "ddd_telefone_1")));

        String street = joinStreet(
            text(node, "descricao_tipo_de_logradouro"),
            text(node, "logradouro"),
            text(node, "numero"),
            text(node, "complemento")
        );
        setIfPresent(company::setStreet, street);

        String cnae = digits(text(node, "cnae_fiscal"));
        if (cnae.length() >= 7) {
            company.setCnaeMain(cnae);
        }
        setIfPresent(company::setCnaeDescription, text(node, "cnae_fiscal_descricao"));
        setIfPresent(company::setLegalNature, text(node, "natureza_juridica"));

        LocalDate openedAt = parseIsoDate(text(node, "data_inicio_atividade"));
        if (openedAt != null) {
            company.setOpenedAt(openedAt);
        }

        company.setDataSource("RECEITA_FEDERAL");
        return extractBrasilApiPartners(node.path("qsa"));
    }

    private List<RegistryPartner> applyOpenCnpj(Company company, JsonNode node) {
        setIfPresent(company::setLegalName, text(node, "razaoSocial"));
        setIfPresent(company::setTradeName, text(node, "nomeFantasia"));
        setIfPresent(company::setCity, text(node, "municipio"));
        setIfPresent(company::setState, text(node, "uf"));
        setIfPresent(company::setNeighborhood, text(node, "bairro"));
        setIfPresent(company::setZipCode, digits(text(node, "cep")));
        setIfPresent(company::setEmail, text(node, "email"));
        setIfPresent(company::setPhone, normalizePhone(text(node, "telefone")));

        String street = joinStreet("RUA", text(node, "logradouro"), text(node, "numero"), text(node, "complemento"));
        setIfPresent(company::setStreet, street);

        JsonNode cnaes = node.path("cnaes");
        if (cnaes.isArray() && !cnaes.isEmpty()) {
            JsonNode main = cnaes.get(0);
            setIfPresent(company::setCnaeMain, digits(text(main, "cnae")));
            setIfPresent(company::setCnaeDescription, text(main, "descricao"));
        }

        setIfPresent(company::setLegalNature, text(node, "naturezaJuridica"));
        LocalDate openedAt = parseBrDate(text(node, "dataInicioAtividades"));
        if (openedAt != null) {
            company.setOpenedAt(openedAt);
        }

        company.setDataSource("RECEITA_FEDERAL");
        return extractOpenCnpjPartners(node.path("socios"));
    }

    private List<RegistryPartner> extractBrasilApiPartners(JsonNode qsa) {
        List<RegistryPartner> partners = new ArrayList<>();
        if (!qsa.isArray()) {
            return partners;
        }
        for (JsonNode item : qsa) {
            String name = text(item, "nome_socio");
            if (!isPresent(name)) {
                continue;
            }
            String role = text(item, "qualificacao_socio");
            partners.add(new RegistryPartner(name, role != null ? role : "Sócio"));
            if (partners.size() >= 3) {
                break;
            }
        }
        return partners;
    }

    private List<RegistryPartner> extractOpenCnpjPartners(JsonNode socios) {
        List<RegistryPartner> partners = new ArrayList<>();
        if (!socios.isArray()) {
            return partners;
        }
        for (JsonNode item : socios) {
            String name = text(item, "nomeSocio");
            if (!isPresent(name)) {
                continue;
            }
            String role = text(item, "descricao");
            partners.add(new RegistryPartner(name, role != null ? role : "Sócio"));
            if (partners.size() >= 3) {
                break;
            }
        }
        return partners;
    }

    private JsonNode fetchJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("User-Agent", properties.getUserAgent())
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception ex) {
            log.debug("Falha ao consultar {}: {}", url, ex.getMessage());
        }
        return null;
    }

    private String joinStreet(String type, String street, String number, String complement) {
        StringBuilder line = new StringBuilder();
        if (isPresent(type)) {
            line.append(type.trim());
        }
        if (isPresent(street)) {
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(street.trim());
        }
        if (isPresent(number)) {
            line.append(' ').append(number.trim());
        }
        if (isPresent(complement)) {
            line.append(' ').append(complement.trim());
        }
        return line.toString().trim();
    }

    private String normalizeCnpj(String cnpj) {
        return cnpj == null ? "" : cnpj.replaceAll("\\D", "");
    }

    private String normalizePhone(String raw) {
        if (!isPresent(raw)) {
            return null;
        }
        return raw.replaceAll("\\D", "");
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return isPresent(value) ? value.trim() : null;
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private LocalDate parseIsoDate(String value) {
        if (!isPresent(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate parseBrDate(String value) {
        if (!isPresent(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, BR_DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    private void setIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (isPresent(value)) {
            setter.accept(value.trim());
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }
}
