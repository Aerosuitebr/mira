package com.prospectportal.module.enrichment.web;

import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.enrichment.EnrichmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebPresenceEnricher {

    private static final Logger log = LoggerFactory.getLogger(WebPresenceEnricher.class);

    private static final Set<String> STOP_WORDS = Set.of(
        "ltda", "sa", "s/a", "me", "epp", "eireli", "servicos", "servico", "comercio", "industria",
        "manutencao", "manutencoes", "aeronautico", "aeronautica", "aeronaves", "de", "da", "do", "das", "dos", "e"
    );

    private static final Set<String> AVIATION_KEYWORDS = Set.of(
        "aeronav", "aviação", "aviacao", "avião", "aviao", "aircraft", "airplane", "hangar",
        "manutenção aeron", "manutencao aeron", "oficina aeron", "mro", "aeroespacial", "aeroespaco",
        "assessoria de voo", "componente aeron", "helice", "hélice", "turbina", "pista"
    );

    private static final Set<String> IT_KEYWORDS = Set.of(
        "ambiente de ti", "data center", "solucoes em nuvem", "soluções em nuvem",
        "locacao de ativos de ti", "locação de ativos de ti", "seguranca da informacao",
        "segurança da informação", "consultoria de monitoramento", "backup e tape",
        "gestao de ambiente de rede", "gestão de ambiente de rede", "infraestrutura de ti",
        "suporte e monitoramento", "cloud", "servidor", "firewall"
    );

    private static final int MIN_SITE_RELEVANCE_SCORE = 52;
    private static final int MAX_SITE_CANDIDATES = 4;

    private static final Set<String> DIRECTORY_DOMAINS = Set.of(
        "cnpj.biz", "econodata.com.br", "casadosdados.com.br", "empresascnpj.com",
        "informecadastral.com.br", "listamais.com.br", "empresaqui.com.br",
        "consultacnpj.com", "cnpja.com", "nexoos.com.br", "telelistas.net",
        "guiamais.com.br", "apontador.com.br", "solutudo.com.br", "tudomais",
        "facebook.com", "instagram.com", "linkedin.com", "youtube.com", "wikipedia.org",
        "google.com", "bing.com"
    );

    private static final List<String> CONTACT_PATHS = List.of(
        "/contato", "/contate-nos", "/contate_nos", "/contact", "/contact-us", "/fale-conosco", "/faleconosco"
    );

    private static final Pattern EMAIL = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MAILTO = Pattern.compile("mailto:([^\"'\\s?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOUDFLARE_EMAIL = Pattern.compile(
        "data-cfemail=\"([0-9a-f]+)\"",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TEL = Pattern.compile("tel:([+\\d\\s().-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHATSAPP = Pattern.compile(
        "(?:wa\\.me/|api\\.whatsapp\\.com/send\\?phone=)(\\d{10,13})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE = Pattern.compile(
        "(?:\\+55\\s?)?(?:\\(?\\d{2}\\)?\\s?)?\\d{4,5}[-\\s]?\\d{4}"
    );
    private static final Pattern LINKEDIN = Pattern.compile(
        "https?://(?:[a-z]{2,3}\\.)?linkedin\\.com/(?:company|in)/[a-zA-Z0-9\\-_./%]+",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INSTAGRAM = Pattern.compile(
        "https?://(?:www\\.)?instagram\\.com/[a-zA-Z0-9._]+/?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FACEBOOK = Pattern.compile(
        "https?://(?:www\\.)?facebook\\.com/[a-zA-Z0-9._-]+/?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTACT_HREF = Pattern.compile(
        "href=[\"']([^\"']*(?:contato|contact|contate|fale-conosco|fale_conosco)[^\"']*)[\"']",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CEP_BLOCK = Pattern.compile(
        "((?:Estrada|Rua|Av\\.?|Avenida|Alameda|Rodovia|Travessa)[^<]{10,180}?CEP[:\\s]*\\d{2}\\.?\\d{3}-?\\d{3}[^<]{0,80})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DDG_URL = Pattern.compile("uddg=([^&\"]+)");

    private final EnrichmentProperties properties;
    private final HttpClient httpClient;

    public WebPresenceEnricher(EnrichmentProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
            .build();
    }

    public WebEnrichmentSignals enrich(Company company) {
        if (!properties.isWebEnabled()) {
            return new WebEnrichmentSignals();
        }

        WebEnrichmentSignals signals = new WebEnrichmentSignals();
        String baseSite = discoverBaseSite(company);
        if (baseSite == null) {
            return signals;
        }

        signals.setWebsiteUrl(baseSite);
        List<String> pages = buildCrawlPlan(baseSite);
        int fetched = 0;

        for (String pageUrl : pages) {
            if (fetched >= properties.getMaxPagesPerCompany() || hasEnoughContact(signals)) {
                break;
            }
            String html = fetch(pageUrl);
            if (html == null || html.isBlank()) {
                continue;
            }
            fetched++;
            extractFromHtml(html, signals, pageUrl);
            if (hasEnoughContact(signals)) {
                break;
            }
            for (String contactUrl : discoverContactLinks(html, baseSite)) {
                if (fetched >= properties.getMaxPagesPerCompany() || hasEnoughContact(signals)) {
                    break;
                }
                if (pages.contains(contactUrl)) {
                    continue;
                }
                String contactHtml = fetch(contactUrl);
                if (contactHtml == null || contactHtml.isBlank()) {
                    continue;
                }
                fetched++;
                extractFromHtml(contactHtml, signals, contactUrl);
                sleep();
            }
            sleep();
        }

        return signals;
    }

    private boolean hasEnoughContact(WebEnrichmentSignals signals) {
        return !signals.getEmails().isEmpty()
            || !signals.getPhones().isEmpty()
            || !signals.getWhatsappPhones().isEmpty();
    }

    /**
     * Avalia se a empresa tem site oficial crawlável para captura de e-mail/telefone,
     * sem varrer todas as páginas de contato.
     */
    public ContactabilityAssessment assessContactability(Company company) {
        if (!properties.isWebEnabled()) {
            return new ContactabilityAssessment(false, "DISABLED", null);
        }

        String baseSite = discoverBaseSite(company);
        if (baseSite == null) {
            return new ContactabilityAssessment(false, "NO_SITE", null);
        }

        String html = fetch(baseSite);
        if (html == null || html.isBlank()) {
            return new ContactabilityAssessment(false, "UNREACHABLE", baseSite);
        }

        WebEnrichmentSignals signals = new WebEnrichmentSignals();
        signals.setWebsiteUrl(baseSite);
        extractFromHtml(html, signals, baseSite);

        boolean hasContactPath = CONTACT_PATHS.stream()
            .anyMatch(path -> html.toLowerCase(Locale.ROOT).contains(path));
        boolean contactable = signals.hasAnyContact() || hasContactPath || hasContactPageLink(html);

        if (!contactable) {
            for (String contactUrl : discoverContactLinks(html, baseSite)) {
                String contactHtml = fetch(contactUrl);
                if (contactHtml == null || contactHtml.isBlank()) {
                    continue;
                }
                extractFromHtml(contactHtml, signals, contactUrl);
                if (signals.hasAnyContact()) {
                    contactable = true;
                    break;
                }
                sleep();
            }
        }

        String status = contactable ? "CONTACTABLE" : "LOW_SIGNAL";
        return new ContactabilityAssessment(contactable, status, baseSite);
    }

    private boolean hasContactPageLink(String html) {
        return CONTACT_HREF.matcher(html).find();
    }

    public record ContactabilityAssessment(boolean contactable, String status, String websiteUrl) {
    }

    private List<String> buildCrawlPlan(String baseSite) {
        Set<String> urls = new LinkedHashSet<>();
        urls.add(baseSite);
        // Prioriza caminhos de contato mais comuns no BR; evita 7 GETs por empresa.
        for (String path : List.of("/contato", "/contact", "/fale-conosco")) {
            urls.add(joinUrl(baseSite, path));
        }
        return new ArrayList<>(urls);
    }

    private String discoverBaseSite(Company company) {
        List<String> candidates = collectSiteCandidates(company);

        String bestUrl = null;
        int bestScore = 0;
        int checked = 0;

        for (String candidate : candidates) {
            if (checked >= MAX_SITE_CANDIDATES) {
                break;
            }
            if (isBlockedSite(candidate)) {
                continue;
            }
            String html = fetch(candidate);
            checked++;
            if (html == null || html.isBlank()) {
                sleep();
                continue;
            }
            int score = scoreSiteRelevance(company, html, candidate);
            log.debug("Site candidato {} pontuou {} para {}", candidate, score, company.getLegalName());
            if (score >= MIN_SITE_RELEVANCE_SCORE && score > bestScore) {
                bestScore = score;
                bestUrl = normalizeSiteUrl(candidate);
                if (score >= 75) {
                    break;
                }
            }
            sleep();
        }

        if (bestUrl == null) {
            log.info("Nenhum site confiável encontrado para {} (mínimo {} pontos)",
                company.getLegalName(), MIN_SITE_RELEVANCE_SCORE);
        }
        return bestUrl;
    }

    private List<String> collectSiteCandidates(Company company) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        if (company.getWebsite() != null && !company.getWebsite().isBlank()) {
            candidates.add(normalizeFetchUrl(company.getWebsite()));
            // Site já conhecido: não perde tempo em DDG nem em slugs especulativos.
            return new ArrayList<>(candidates);
        }

        int searchAdded = 0;
        for (String url : searchWebPresenceCandidates(company)) {
            candidates.add(url);
            if (++searchAdded >= 2) {
                break;
            }
        }

        candidates.addAll(buildSiteCandidates(company));
        return new ArrayList<>(candidates);
    }

    private List<String> buildSiteCandidates(Company company) {
        List<String> slugs = buildSlugs(company);
        slugs.sort((a, b) -> Integer.compare(b.length(), a.length()));

        Set<String> urls = new LinkedHashSet<>();
        int slugLimit = Math.min(2, slugs.size());
        for (int i = 0; i < slugLimit; i++) {
            String slug = slugs.get(i);
            urls.add("https://www." + slug + ".com.br");
            urls.add("https://" + slug + ".com.br");
        }
        return new ArrayList<>(urls);
    }

    private List<String> discoverContactLinks(String html, String baseSite) {
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = CONTACT_HREF.matcher(html);
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            String absolute = toAbsoluteUrl(baseSite, href);
            if (absolute != null) {
                links.add(absolute);
            }
        }
        return new ArrayList<>(links);
    }

    private void extractFromHtml(String html, WebEnrichmentSignals signals, String pageUrl) {
        String text = htmlToText(html);

        Matcher mailtoMatcher = MAILTO.matcher(html);
        while (mailtoMatcher.find()) {
            addEmail(signals, mailtoMatcher.group(1));
        }

        Matcher cfMatcher = CLOUDFLARE_EMAIL.matcher(html);
        while (cfMatcher.find()) {
            String decoded = decodeCloudflareEmail(cfMatcher.group(1));
            if (decoded != null) {
                addEmail(signals, decoded);
            }
        }

        Matcher emailMatcher = EMAIL.matcher(text);
        while (emailMatcher.find()) {
            addEmail(signals, emailMatcher.group());
        }

        Matcher telMatcher = TEL.matcher(html);
        while (telMatcher.find()) {
            addPhone(signals, telMatcher.group(1));
        }

        Matcher waMatcher = WHATSAPP.matcher(html);
        while (waMatcher.find()) {
            addWhatsapp(signals, waMatcher.group(1));
        }

        Matcher phoneMatcher = PHONE.matcher(text);
        while (phoneMatcher.find()) {
            addPhone(signals, phoneMatcher.group());
        }

        Matcher cepMatcher = CEP_BLOCK.matcher(text);
        while (cepMatcher.find()) {
            String address = cepMatcher.group(1).replaceAll("\\s+", " ").trim();
            if (address.length() >= 20) {
                signals.getAddresses().add(address);
            }
        }

        extractUrls(html, LINKEDIN, signals.getLinkedinUrls());
        extractUrls(html, INSTAGRAM, signals.getInstagramUrls());
        extractUrls(html, FACEBOOK, signals.getFacebookUrls());

        if (pageUrl.contains("instagram.com")) {
            signals.getInstagramUrls().add(cleanUrl(pageUrl));
        }
    }

    private String htmlToText(String html) {
        return html
            .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
            .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
            .replaceAll("<[^>]+>", " ")
            .replace("&#64;", "@")
            .replace("&commat;", "@")
            .replace("&nbsp;", " ");
    }

    private void addEmail(WebEnrichmentSignals signals, String raw) {
        String email = raw.toLowerCase(Locale.ROOT).trim();
        if (!isNoiseEmail(email)) {
            signals.getEmails().add(email);
        }
    }

    private String decodeCloudflareEmail(String encoded) {
        if (encoded == null || encoded.length() < 4 || encoded.length() % 2 != 0) {
            return null;
        }
        try {
            int key = Integer.parseInt(encoded.substring(0, 2), 16);
            StringBuilder email = new StringBuilder();
            for (int i = 2; i < encoded.length(); i += 2) {
                int code = Integer.parseInt(encoded.substring(i, i + 2), 16) ^ key;
                email.append((char) code);
            }
            return email.toString();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void addPhone(WebEnrichmentSignals signals, String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() > 11) {
            digits = digits.substring(2);
        }
        if (!isValidBrazilPhone(digits)) {
            return;
        }
        if (digits.length() == 11 && digits.charAt(2) == '9') {
            signals.getWhatsappPhones().add(digits);
        } else {
            signals.getPhones().add(digits);
        }
    }

    private void addWhatsapp(WebEnrichmentSignals signals, String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() > 11) {
            digits = digits.substring(2);
        }
        if (isValidBrazilPhone(digits)) {
            signals.getWhatsappPhones().add(digits);
        }
    }

    private boolean isValidBrazilPhone(String digits) {
        if (digits.length() != 10 && digits.length() != 11) {
            return false;
        }
        int ddd = Integer.parseInt(digits.substring(0, 2));
        return ddd >= 11 && ddd <= 99;
    }

    private List<String> searchWebPresenceCandidates(Company company) {
        String query = buildSearchQuery(company);
        if (query.isBlank()) {
            return List.of();
        }
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String html = fetch("https://html.duckduckgo.com/html/?q=" + encoded);
        if (html == null) {
            return List.of();
        }

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher matcher = DDG_URL.matcher(html);
        while (matcher.find()) {
            String decoded = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
            if (looksLikeCompanySite(decoded)) {
                urls.add(decoded);
            }
            if (decoded.contains("linkedin.com/company")) {
                urls.add(decoded);
            }
        }
        return new ArrayList<>(urls);
    }

    private int scoreSiteRelevance(Company company, String html, String url) {
        if (isBlockedSite(url)) {
            return 0;
        }
        String text = htmlToText(html).toLowerCase(Locale.ROOT);
        if (isDirectoryLikePage(text)) {
            return 0;
        }
        int score = 0;

        List<String> nameTokens = extractSignificantNameTokens(company);
        long matchedNameTokens = nameTokens.stream().filter(text::contains).count();
        score += (int) Math.min(36, matchedNameTokens * 12);

        if (company.getCnpj() != null) {
            String digits = company.getCnpj().replaceAll("\\D", "");
            if (text.contains(digits) || text.contains(formatCnpj(digits))) {
                score += 40;
            }
        }

        if (company.getCity() != null && text.contains(company.getCity().toLowerCase(Locale.ROOT))) {
            score += 12;
        }
        if (company.getState() != null && text.contains(company.getState().toLowerCase(Locale.ROOT))) {
            score += 4;
        }

        score += industryKeywordScore(company, text);
        score += domainMatchScore(company, url);

        if (isAviationCompany(company)) {
            score -= itMismatchPenalty(text);
            boolean aviationContent = industryKeywordScore(company, text) >= 8;
            String host = extractHost(url);
            boolean aviationDomain = host != null && (host.contains("aero") || host.contains("aviac"));
            if (!aviationContent && !aviationDomain) {
                score = Math.min(score, 35);
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    private int industryKeywordScore(Company company, String text) {
        if (!isAviationCompany(company)) {
            return 0;
        }
        int hits = 0;
        for (String keyword : AVIATION_KEYWORDS) {
            if (text.contains(keyword)) {
                hits++;
            }
        }
        return Math.min(30, hits * 8);
    }

    private int itMismatchPenalty(String text) {
        int penalty = 0;
        for (String keyword : IT_KEYWORDS) {
            if (text.contains(keyword)) {
                penalty += 18;
            }
        }
        return Math.min(72, penalty);
    }

    private int domainMatchScore(Company company, String url) {
        String host = extractHost(url);
        if (host == null) {
            return 0;
        }
        host = host.toLowerCase(Locale.ROOT).replace("www.", "");
        String hostCompact = host.replaceAll("[^a-z0-9]", "");

        int score = 0;
        for (String token : extractSignificantNameTokens(company)) {
            if (token.length() >= 4 && hostCompact.contains(token)) {
                score += 10;
            }
        }

        if (isAviationCompany(company)) {
            if (hostCompact.contains("aero") || hostCompact.contains("aviac") || hostCompact.contains("aviao")) {
                score += 18;
            }
        }
        return Math.min(28, score);
    }

    private boolean isAviationCompany(Company company) {
        String cnae = company.getCnaeMain() != null ? company.getCnaeMain() : "";
        String description = company.getCnaeDescription() != null ? company.getCnaeDescription().toLowerCase(Locale.ROOT) : "";
        return cnae.startsWith("331") || description.contains("aeronav") || description.contains("avia");
    }

    private List<String> extractSignificantNameTokens(Company company) {
        Set<String> tokens = new LinkedHashSet<>();
        if (company.getTradeName() != null) {
            collectSignificantTokens(company.getTradeName(), tokens);
        }
        if (company.getLegalName() != null) {
            collectSignificantTokens(company.getLegalName(), tokens);
        }
        return new ArrayList<>(tokens);
    }

    private void collectSignificantTokens(String raw, Set<String> target) {
        for (String token : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+")) {
            if (token.length() >= 4 && !STOP_WORDS.contains(token)) {
                target.add(token);
            }
        }
    }

    private String formatCnpj(String digits) {
        if (digits.length() != 14) {
            return digits;
        }
        return String.format("%s.%s.%s/%s-%s",
            digits.substring(0, 2),
            digits.substring(2, 5),
            digits.substring(5, 8),
            digits.substring(8, 12),
            digits.substring(12, 14));
    }

    private boolean isDirectoryLikePage(String text) {
        return text.contains("consultar cnpj") || text.contains("dados cadastrais")
            || (text.contains("receita federal") && text.contains("consulta"))
            || text.contains("empresas neste endereço")
            || text.contains("guia de empresas") || text.contains("anuncie sua empresa")
            || text.contains("classificados") || text.contains("catálogo de empresas")
            || text.contains("encontre empresas") || text.contains("empresas em ")
            || text.contains("solutudo");
    }

    private String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean looksLikeCompanySite(String url) {
        if (isBlockedSite(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return !lower.contains("facebook.com") && !lower.contains("instagram.com")
            && !lower.contains("youtube.com") && !lower.contains("wikipedia.org");
    }

    private boolean isBlockedSite(String url) {
        String host = extractHost(url);
        if (host == null) {
            return true;
        }
        host = host.toLowerCase(Locale.ROOT).replace("www.", "");
        for (String blocked : DIRECTORY_DOMAINS) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
    }

    private String buildSearchQuery(Company company) {
        String name = firstNonBlank(company.getTradeName(), company.getLegalName());
        if (name == null) {
            return "";
        }
        String shortName = shortenCompanyName(name);
        String city = company.getCity() != null ? company.getCity() : "";
        String industryHint = isAviationCompany(company) ? "manutenção aeronaves aviação" : "";
        return (shortName + " " + city + " " + industryHint + " site oficial contato").trim();
    }

    private List<String> buildSlugs(Company company) {
        Set<String> slugs = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        if (company.getTradeName() != null && !company.getTradeName().isBlank()) {
            names.add(company.getTradeName());
        }
        if (company.getLegalName() != null && !company.getLegalName().isBlank()) {
            names.add(company.getLegalName());
        }
        for (String raw : names) {
            String[] tokens = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .split("\\s+");
            List<String> significant = new ArrayList<>();
            for (String token : tokens) {
                if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                    significant.add(token);
                }
            }

            if (!significant.isEmpty()) {
                slugs.add(String.join("", significant));
            }
            if (significant.size() >= 2) {
                slugs.add(significant.get(0) + significant.get(1));
            }
            if (significant.size() >= 3) {
                slugs.add(significant.get(0) + significant.get(1) + significant.get(2));
            }

            if (isAviationCompany(company)) {
                List<String> aviationSlugs = new ArrayList<>(slugs);
                for (String base : aviationSlugs) {
                    if (base.length() >= 4) {
                        slugs.add(base + "aviacao");
                        slugs.add(base + "aero");
                        slugs.add(base + "aeronautica");
                    }
                }
            }
        }
        slugs.removeIf(slug -> slug.length() < 6);
        return new ArrayList<>(slugs);
    }

    private String shortenCompanyName(String name) {
        String[] tokens = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");
        List<String> significant = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                significant.add(token);
            }
        }
        if (significant.size() >= 2) {
            return significant.get(0) + " " + significant.get(1);
        }
        return name;
    }

    private void extractUrls(String html, Pattern pattern, Set<String> target) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            target.add(cleanUrl(matcher.group()));
        }
    }

    private boolean isNoiseEmail(String email) {
        return email.endsWith(".png") || email.endsWith(".jpg") || email.contains("example.com")
            || email.contains("sentry.io") || email.contains("wixpress.com")
            || email.contains("wordpress.com") || email.contains("sentry-next")
            || email.contains("[email") || email.contains("email protected");
    }

    private String fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                .header("User-Agent", properties.getUserAgent())
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return response.body();
            }
        } catch (Exception ex) {
            log.debug("Falha ao buscar {}: {}", url, ex.getMessage());
        }
        return null;
    }

    private String normalizeFetchUrl(String website) {
        String value = website.trim();
        if (!value.startsWith("http")) {
            value = "https://" + value;
        }
        return value;
    }

    private String normalizeSiteUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null) {
                return url;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception ex) {
            return url;
        }
    }

    private String joinUrl(String base, String path) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        return base + path;
    }

    private String toAbsoluteUrl(String baseSite, String href) {
        try {
            if (href.startsWith("http")) {
                return cleanUrl(href);
            }
            URI base = URI.create(baseSite);
            return cleanUrl(base.resolve(href).toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private String cleanUrl(String url) {
        return url.replaceAll("[\"'<>].*$", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void sleep() {
        try {
            Thread.sleep(properties.getRateLimitMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
