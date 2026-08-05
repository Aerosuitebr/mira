package com.prospectportal.module.discovery;

import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.geo.CityCoordinateResolver;
import com.prospectportal.module.geo.GeocodingService;
import com.prospectportal.module.search.CompanySearchService;
import com.prospectportal.web.dto.CompanyResponse;
import com.prospectportal.web.dto.PageResponse;
import com.prospectportal.web.mapper.DtoMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DiscoveryService {

    private final CompanyRepository companyRepository;
    private final CompanySearchService companySearchService;
    private final CityCoordinateResolver cityCoordinateResolver;
    private final GeocodingService geocodingService;
    private final SearchResultEnrichmentService searchResultEnrichmentService;
    private final TradeNameSearchGuard tradeNameSearchGuard;
    private final FlowTestFixtureService flowTestFixtureService;

    public DiscoveryService(
        CompanyRepository companyRepository,
        ObjectProvider<CompanySearchService> companySearchService,
        CityCoordinateResolver cityCoordinateResolver,
        GeocodingService geocodingService,
        SearchResultEnrichmentService searchResultEnrichmentService,
        TradeNameSearchGuard tradeNameSearchGuard,
        FlowTestFixtureService flowTestFixtureService
    ) {
        this.companyRepository = companyRepository;
        this.companySearchService = companySearchService.getIfAvailable();
        this.cityCoordinateResolver = cityCoordinateResolver;
        this.geocodingService = geocodingService;
        this.searchResultEnrichmentService = searchResultEnrichmentService;
        this.tradeNameSearchGuard = tradeNameSearchGuard;
        this.flowTestFixtureService = flowTestFixtureService;
    }

    public PageResponse<CompanyResponse> search(
        String keyword,
        String cnae,
        String state,
        String city,
        String revenue,
        boolean activeOnly,
        boolean contactableOnly,
        int page,
        int size
    ) {
        if (flowTestFixtureService.isEnabled()) {
            return flowTestFixtureService.asPage(page, size);
        }

        CnaeFilter cnaeFilter = CnaeFilter.parse(cnae);
        String normalizedState = normalizeState(state);
        String normalizedKeyword = blankToEmpty(keyword);

        Optional<PageResponse<CompanyResponse>> cnpjHit = searchByCnpjIfApplicable(
            normalizedKeyword,
            cnaeFilter,
            normalizedState,
            blankToEmpty(city),
            blankToEmpty(revenue),
            activeOnly,
            contactableOnly,
            page,
            size
        );
        if (cnpjHit.isPresent()) {
            return cnpjHit.get();
        }

        // Elasticsearch é o mecanismo principal para texto e filtros estruturados.
        // O PostgreSQL permanece como fallback quando o índice está vazio ou indisponível.
        boolean useElasticsearch = !contactableOnly
            && companySearchService != null
            && companySearchService.isIndexPopulated();

        if (useElasticsearch) {
            try {
                PageResponse<CompanyResponse> esResult = companySearchService.search(
                    blankToNull(keyword),
                    blankToNull(cnae),
                    normalizedState,
                    blankToNull(city),
                    blankToNull(revenue),
                    activeOnly,
                    page,
                    size
                );
                if (esResult.totalElements() > 0) {
                    // Resposta direta do ES: sem hidratar Postgres nem geo_cache no hot path.
                    return esResult;
                }
                // Índice ES vazio ou dessincronizado: confirma no PostgreSQL antes de devolver zero.
            } catch (Exception ex) {
                // fallback PostgreSQL se Elasticsearch indisponível
            }
        }

        return searchPostgres(
            normalizedKeyword,
            cnaeFilter,
            normalizedState,
            city,
            revenue,
            activeOnly,
            contactableOnly,
            page,
            size
        );
    }

    private Optional<PageResponse<CompanyResponse>> searchByCnpjIfApplicable(
        String keyword,
        CnaeFilter cnaeFilter,
        String normalizedState,
        String city,
        String revenue,
        boolean activeOnly,
        boolean contactableOnly,
        int page,
        int size
    ) {
        String digits = digitsOnly(keyword);
        if (digits.length() < 8) {
            return Optional.empty();
        }

        if (digits.length() == 14) {
            Optional<Company> exact = companyRepository.findByCnpj(digits);
            if (exact.isPresent() && matchesQuickFilters(exact.get(), cnaeFilter, normalizedState, city, revenue, activeOnly, contactableOnly)) {
                CompanyResponse response = withMapCoordinates(DtoMapper.toCompany(exact.get()));
                return Optional.of(singlePage(response, page, size));
            }
        }

        if (page > 0 || normalizedState != null || contactableOnly) {
            return Optional.empty();
        }

        List<Company> matches = companyRepository.searchNationalByCnpjPrefix(
            digits,
            cnaeFilter.mode(),
            cnaeFilter.value(),
            cnaePrefixes(cnaeFilter),
            city,
            revenue,
            activeOnly,
            contactableOnly,
            size,
            0
        );
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        List<CompanyResponse> content = matches.stream()
            .map(DtoMapper::toCompany)
            .map(this::withMapCoordinates)
            .toList();
        scheduleEnrichment(content);
        return Optional.of(new PageResponse<>(content, content.size(), 1, page, size));
    }

    private boolean matchesQuickFilters(
        Company company,
        CnaeFilter cnaeFilter,
        String state,
        String city,
        String revenue,
        boolean activeOnly,
        boolean contactableOnly
    ) {
        if (state != null && !state.isBlank() && !state.equalsIgnoreCase(company.getState())) {
            return false;
        }
        if (city != null && !city.isBlank() && (company.getCity() == null || !company.getCity().toLowerCase().contains(city.toLowerCase()))) {
            return false;
        }
        if (revenue != null && !revenue.isBlank() && !revenue.equals(company.getEstimatedRevenue())) {
            return false;
        }
        if (activeOnly && !"02".equals(company.getRegistrationStatus())) {
            return false;
        }
        if (contactableOnly && !company.isWebContactable()) {
            return false;
        }
        return matchesCnaeFilter(company, cnaeFilter);
    }

    private boolean matchesCnaeFilter(Company company, CnaeFilter cnaeFilter) {
        return switch (cnaeFilter.mode()) {
            case CnaeFilter.MODE_NONE -> true;
            case CnaeFilter.MODE_EXACT -> cnaeFilter.value().equals(company.getCnaeMain())
                || (company.getCnaeSecondary() != null && company.getCnaeSecondary().contains(cnaeFilter.value()));
            case CnaeFilter.MODE_PREFIX -> company.getCnaeMain() != null && company.getCnaeMain().startsWith(cnaeFilter.value())
                || (company.getCnaeSecondary() != null && company.getCnaeSecondary().contains(cnaeFilter.value()));
            case CnaeFilter.MODE_SECTIONS -> {
                String mainPrefix = company.getCnaeMain() != null && company.getCnaeMain().length() >= 2
                    ? company.getCnaeMain().substring(0, 2)
                    : "";
                yield cnaeFilter.prefixes().contains(mainPrefix)
                    || (company.getCnaeSecondary() != null && cnaeFilter.prefixes().stream().anyMatch(company.getCnaeSecondary()::contains));
            }
            default -> true;
        };
    }

    private PageResponse<CompanyResponse> singlePage(CompanyResponse company, int page, int size) {
        if (page > 0) {
            return new PageResponse<>(List.of(), 1, 1, page, size);
        }
        scheduleEnrichment(List.of(company));
        return new PageResponse<>(List.of(company), 1, 1, page, size);
    }

    private void scheduleEnrichment(List<CompanyResponse> companies) {
        List<UUID> ids = companies.stream().map(CompanyResponse::id).toList();
        if (!ids.isEmpty()) {
            searchResultEnrichmentService.enrichSearchResults(ids);
        }
    }

    private PageResponse<CompanyResponse> searchPostgres(
        String keyword,
        CnaeFilter cnaeFilter,
        String normalizedState,
        String city,
        String revenue,
        boolean activeOnly,
        boolean contactableOnly,
        int page,
        int size
    ) {
        if ((normalizedState == null || normalizedState.isBlank()) && !keyword.isBlank() && !contactableOnly) {
            return searchPostgresNationalFast(
                keyword,
                cnaeFilter,
                city,
                revenue,
                activeOnly,
                contactableOnly,
                page,
                size
            );
        }

        Page<Company> result = companyRepository.search(
            blankToEmpty(keyword),
            cnaeFilter.mode(),
            cnaeFilter.value(),
            cnaeFilter.prefixUpperBound(),
            cnaePrefixes(cnaeFilter),
            normalizedState == null ? "" : normalizedState,
            blankToEmpty(city),
            blankToEmpty(revenue),
            activeOnly,
            contactableOnly,
            PageRequest.of(page, size)
        );

        List<CompanyResponse> content = result.getContent().stream()
            .map(DtoMapper::toCompany)
            .map(this::withMapCoordinates)
            .toList();
        return new PageResponse<>(content, result.getTotalElements(), result.getTotalPages(), page, size);
    }

    private PageResponse<CompanyResponse> searchPostgresNationalFast(
        String keyword,
        CnaeFilter cnaeFilter,
        String city,
        String revenue,
        boolean activeOnly,
        boolean contactableOnly,
        int page,
        int size
    ) {
        String namePrefix = keyword.trim();
        if (namePrefix.length() < 2) {
            return new PageResponse<>(List.of(), 0, 0, page, size);
        }

        String cnpjPrefix = digitsOnly(keyword);
        int offset = page * size;
        List<Company> matches;

        if (cnpjPrefix.length() >= 8) {
            if (cnpjPrefix.length() > 14) {
                cnpjPrefix = cnpjPrefix.substring(0, 14);
            }
            matches = companyRepository.searchNationalByCnpjPrefix(
                cnpjPrefix,
                cnaeFilter.mode(),
                cnaeFilter.value(),
                cnaePrefixes(cnaeFilter),
                blankToEmpty(city),
                blankToEmpty(revenue),
                activeOnly,
                contactableOnly,
                size,
                offset
            );
        } else {
            matches = searchNationalByName(
                namePrefix,
                cnaeFilter,
                city,
                revenue,
                activeOnly,
                contactableOnly,
                size,
                offset
            );
        }

        List<CompanyResponse> content = matches.stream()
            .map(DtoMapper::toCompany)
            .map(this::withMapCoordinates)
            .toList();

        long totalElements = offset + content.size();
        if (content.size() == size) {
            totalElements++;
        }
        int totalPages = content.isEmpty() ? 0 : page + (content.size() == size ? 2 : 1);
        return new PageResponse<>(content, totalElements, totalPages, page, size);
    }

    private List<Company> searchNationalByName(
        String nameTerm,
        CnaeFilter cnaeFilter,
        String city,
        String revenue,
        boolean activeOnly,
        boolean contactableOnly,
        int limit,
        int offset
    ) {
        String mode = cnaeFilter.mode();
        String value = cnaeFilter.value();
        List<String> prefixes = cnaePrefixes(cnaeFilter);
        String cityFilter = blankToEmpty(city);
        String revenueFilter = blankToEmpty(revenue);

        List<Company> matches = companyRepository.searchNationalByLegalNamePrefix(
            nameTerm, mode, value, prefixes, cityFilter, revenueFilter, activeOnly, contactableOnly, limit, offset
        );
        if (!matches.isEmpty()) {
            return matches;
        }

        matches = companyRepository.searchNationalByLegalNameContains(
            nameTerm, mode, value, prefixes, cityFilter, revenueFilter, activeOnly, contactableOnly, limit, offset
        );
        if (!matches.isEmpty()) {
            return matches;
        }

        if (!tradeNameSearchGuard.isTradeNameIndexReady()) {
            return List.of();
        }

        matches = companyRepository.searchNationalByTradeNamePrefix(
            nameTerm, mode, value, prefixes, cityFilter, revenueFilter, activeOnly, contactableOnly, limit, offset
        );
        if (!matches.isEmpty()) {
            return matches;
        }

        return companyRepository.searchNationalByTradeNameContains(
            nameTerm, mode, value, prefixes, cityFilter, revenueFilter, activeOnly, contactableOnly, limit, offset
        );
    }

    @Cacheable(
        value = "geoSearch",
        key = "#latitude + '-' + #longitude + '-' + #radiusKm + '-' + #cnae + '-' + #limit",
        unless = "#result == null or #result.isEmpty()"
    )
    public List<CompanyResponse> searchByRadius(double latitude, double longitude, double radiusKm, String cnae, int limit) {
        if (flowTestFixtureService.isEnabled()) {
            return flowTestFixtureService.loadResponses();
        }

        // Elasticsearch é o caminho principal para raio (geo_point já indexado).
        if (companySearchService != null && companySearchService.isIndexPopulated()) {
            try {
                List<CompanyResponse> esResult = companySearchService.searchByRadius(
                    latitude,
                    longitude,
                    radiusKm,
                    blankToNull(cnae),
                    limit
                );
                if (!esResult.isEmpty()) {
                    return esResult;
                }
            } catch (Exception ex) {
                // fallback PostGIS se Elasticsearch indisponível
            }
        }

        double radiusMeters = radiusKm * 1000;
        CnaeFilter cnaeFilter = CnaeFilter.parse(cnae);
        return companyRepository.findWithinRadius(
                latitude,
                longitude,
                radiusMeters,
                cnaeFilter.mode(),
                cnaeFilter.value(),
                cnaePrefixes(cnaeFilter),
                limit
            )
            .stream()
            .map(DtoMapper::toCompany)
            .toList();
    }

    public List<CompanyResponse> findByIds(String idsParam) {
        if (idsParam == null || idsParam.isBlank()) {
            return List.of();
        }
        List<UUID> ids = java.util.Arrays.stream(idsParam.split(","))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .map(UUID::fromString)
            .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return companyRepository.findAllById(ids).stream()
            .filter(company -> flowTestFixtureService.isEnabled()
                || !FlowTestFixtureService.DATA_SOURCE.equals(company.getDataSource()))
            .map(DtoMapper::toCompany)
            .map(this::withMapCoordinates)
            .toList();
    }

    public List<CompanyResponse> refineMapCoordinates(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<UUID, CompanyResponse> refinedById = new java.util.LinkedHashMap<>();
        for (Company company : companyRepository.findAllById(ids)) {
            refinedById.put(company.getId(), withMapCoordinates(DtoMapper.toCompany(company)));
        }

        searchResultEnrichmentService.enrichSearchResults(ids);

        return ids.stream()
            .map(refinedById::get)
            .filter(Objects::nonNull)
            .toList();
    }

    private void applyMapCoordinate(Company company, GeocodingService.MapCoordinate coordinate) {
        company.setLatitude(coordinate.latitude());
        company.setLongitude(coordinate.longitude());
        company.setGeocoded(coordinate.geocoded());
        company.setLocationPrecision(coordinate.precision());
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalizeState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return state.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private List<String> cnaePrefixes(CnaeFilter filter) {
        if (CnaeFilter.MODE_SECTIONS.equals(filter.mode())) {
            return filter.prefixes();
        }
        return List.of("");
    }

    private PageResponse<CompanyResponse> withMapCoordinatesPage(PageResponse<CompanyResponse> page) {
        List<CompanyResponse> content = page.content().stream()
            .map(this::withMapCoordinates)
            .toList();
        return new PageResponse<>(content, page.totalElements(), page.totalPages(), page.page(), page.size());
    }

    private CompanyResponse withMapCoordinates(CompanyResponse company) {
        if ("EXACT".equals(company.locationPrecision()) && company.latitude() != null && company.longitude() != null) {
            return company;
        }

        Optional<GeocodingService.MapCoordinate> resolved = geocodingService.resolveForMap(
            company.street(),
            company.city(),
            company.state(),
            company.zipCode(),
            company.latitude(),
            company.longitude(),
            company.locationPrecision()
        );
        if (resolved.isPresent()) {
            GeocodingService.MapCoordinate coordinate = resolved.get();
            return copyWithCoordinates(company, coordinate.latitude(), coordinate.longitude(), coordinate.precision(), coordinate.geocoded());
        }

        if (company.latitude() != null && company.longitude() != null) {
            return company;
        }

        double[] coords = cityCoordinateResolver.resolveForMap(
            company.latitude(),
            company.longitude(),
            company.zipCode(),
            company.city(),
            company.state()
        );
        if (coords == null || coords.length < 2) {
            return company;
        }
        String precision = company.locationPrecision();
        if (precision == null || precision.isBlank() || "UNRESOLVED".equals(precision)) {
            precision = company.zipCode() != null && !company.zipCode().isBlank() ? "CEP" : "CITY";
        }
        return copyWithCoordinates(company, coords[0], coords[1], precision, false);
    }

    private CompanyResponse copyWithCoordinates(
        CompanyResponse company,
        double latitude,
        double longitude,
        String precision,
        boolean geocoded
    ) {
        return new CompanyResponse(
            company.id(),
            company.cnpj(),
            company.legalName(),
            company.tradeName(),
            company.cnaeMain(),
            company.cnaeSecondary(),
            company.cnaeDescription(),
            company.city(),
            company.state(),
            company.neighborhood(),
            company.street(),
            company.zipCode(),
            company.capitalSocial(),
            company.openedAt(),
            company.estimatedRevenue(),
            company.website(),
            company.email(),
            company.phone(),
            latitude,
            longitude,
            geocoded,
            precision,
            company.webContactable()
        );
    }

    private PageResponse<CompanyResponse> hydrateCompanies(PageResponse<CompanyResponse> page) {
        List<CompanyResponse> hydrated = hydrateCompaniesList(page.content());
        return new PageResponse<>(hydrated, page.totalElements(), page.totalPages(), page.page(), page.size());
    }

    private List<CompanyResponse> hydrateCompaniesList(List<CompanyResponse> companies) {
        List<UUID> ids = companies.stream()
            .map(CompanyResponse::id)
            .toList();
        if (ids.isEmpty()) {
            return companies;
        }

        Map<UUID, Company> byId = companyRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Company::getId, Function.identity()));
        if (byId.isEmpty()) {
            return companies;
        }

        return companies.stream()
            .map(company -> byId.containsKey(company.id())
                ? DtoMapper.toCompany(byId.get(company.id()))
                : company)
            .toList();
    }
}
