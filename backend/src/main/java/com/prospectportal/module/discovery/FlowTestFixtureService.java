package com.prospectportal.module.discovery;

import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.web.dto.CompanyResponse;
import com.prospectportal.web.dto.PageResponse;
import com.prospectportal.web.mapper.DtoMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Quatro pessoas/empresas fixas para testar o fluxo completo
 * (Descobrir → Enriquecer → Abordar → Propostas / Agenda).
 *
 * <pre>
 * Wellem Mello de Lyra       · wellemlyra@gmail.com            · (sem telefone → e-mail)
 *   Rua Engenheiro Oscar Weinschenk, 30 - Vila da Penha
 *
 * Thiago Lago de Lyra        · thiagolyra18@gmail.com          · (sem telefone → e-mail)
 *   Estrada Coronel Vieira, 136, bl 2
 *
 * Daniel Felipe Lago de Lyra · danielfelipe.l.lyra@gmail.com   · 21978309389
 *   Rua Canudos, 187 - Irajá
 *
 * Luis Henrique Nascimento   · henri.geel21@gmail.com          · (sem telefone → e-mail)
 *   Rua Frei Vicente, 15
 * </pre>
 */
@Service
public class FlowTestFixtureService {

    private static final Logger log = LoggerFactory.getLogger(FlowTestFixtureService.class);

    public static final String DATA_SOURCE = "FLOW_TEST";

    public static final UUID WELLEM_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000001");
    public static final UUID THIAGO_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000002");
    public static final UUID DANIEL_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000003");
    public static final UUID LUIS_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000004");

    private final CompanyRepository companyRepository;
    private final boolean enabled;
    private final AtomicReference<List<CompanyResponse>> cachedResponses = new AtomicReference<>(List.of());

    public FlowTestFixtureService(
        CompanyRepository companyRepository,
        @Value("${app.discovery.test-fixtures-enabled:false}") boolean enabled
    ) {
        this.companyRepository = companyRepository;
        this.enabled = enabled;
    }

    @PostConstruct
    void warmCache() {
        if (!enabled) {
            return;
        }
        try {
            refreshCache();
            log.info("Fixtures de fluxo ativas: {} empresa(s) em cache", cachedResponses.get().size());
        } catch (Exception ex) {
            log.warn("Não foi possível pré-carregar fixtures FLOW_TEST: {}", ex.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Company> loadCompanies() {
        return companyRepository.findByDataSourceOrderByTradeNameAsc(DATA_SOURCE);
    }

    public List<CompanyResponse> loadResponses() {
        List<CompanyResponse> cached = cachedResponses.get();
        if (!cached.isEmpty()) {
            return cached;
        }
        refreshCache();
        return cachedResponses.get();
    }

    public synchronized void refreshCache() {
        List<CompanyResponse> loaded = loadCompanies().stream().map(DtoMapper::toCompany).toList();
        cachedResponses.set(List.copyOf(loaded));
    }

    public PageResponse<CompanyResponse> asPage(int page, int size) {
        List<CompanyResponse> all = loadResponses();
        int safeSize = Math.max(1, size);
        int from = Math.max(0, page) * safeSize;
        if (from >= all.size()) {
            return new PageResponse<>(List.of(), all.size(), pageCount(all.size(), safeSize), page, safeSize);
        }
        int to = Math.min(all.size(), from + safeSize);
        return new PageResponse<>(all.subList(from, to), all.size(), pageCount(all.size(), safeSize), page, safeSize);
    }

    private static int pageCount(int total, int size) {
        return total == 0 ? 0 : (int) Math.ceil(total / (double) size);
    }
}
