package com.prospectportal.module.geo;

import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.ingestion.entity.ImportJob;
import com.prospectportal.module.ingestion.repository.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final Pattern STREET_NUMBER = Pattern.compile("^(.*\\D)\\s*0*(\\d+)\\s*$");
    private static final List<String> GEOCODE_STATE_PRIORITY = List.of("SP", "RJ", "MG", "ES", "GO", "DF", "MT", "MS");
    private static final Map<String, String> STATE_NAMES = Map.ofEntries(
        Map.entry("AC", "Acre"),
        Map.entry("AL", "Alagoas"),
        Map.entry("AP", "Amapá"),
        Map.entry("AM", "Amazonas"),
        Map.entry("BA", "Bahia"),
        Map.entry("CE", "Ceará"),
        Map.entry("DF", "Distrito Federal"),
        Map.entry("ES", "Espírito Santo"),
        Map.entry("GO", "Goiás"),
        Map.entry("MA", "Maranhão"),
        Map.entry("MT", "Mato Grosso"),
        Map.entry("MS", "Mato Grosso do Sul"),
        Map.entry("MG", "Minas Gerais"),
        Map.entry("PA", "Pará"),
        Map.entry("PB", "Paraíba"),
        Map.entry("PR", "Paraná"),
        Map.entry("PE", "Pernambuco"),
        Map.entry("PI", "Piauí"),
        Map.entry("RJ", "Rio de Janeiro"),
        Map.entry("RN", "Rio Grande do Norte"),
        Map.entry("RS", "Rio Grande do Sul"),
        Map.entry("RO", "Rondônia"),
        Map.entry("RR", "Roraima"),
        Map.entry("SC", "Santa Catarina"),
        Map.entry("SP", "São Paulo"),
        Map.entry("SE", "Sergipe"),
        Map.entry("TO", "Tocantins")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ImportJobRepository importJobRepository;
    private final RestClient nominatimClient;
    private final RestClient awesomeCepClient;
    private final RestClient brasilApiCepClient;
    private final long rateLimitMs;
    private final long cepRateLimitMs;
    private final int cepParallelism;
    private final int cepBatchSize;
    private final Object cepApiLock = new Object();
    private long nextCepApiAllowedAtMs;

    public GeocodingService(
        JdbcTemplate jdbcTemplate,
        ImportJobRepository importJobRepository,
        @Value("${app.geocoding.nominatim-url}") String nominatimUrl,
        @Value("${app.geocoding.awesome-cep-url:https://cep.awesomeapi.com.br}") String awesomeCepUrl,
        @Value("${app.geocoding.brasil-api-cep-url:https://brasilapi.com.br/api/cep/v2}") String brasilApiCepUrl,
        @Value("${app.geocoding.rate-limit-ms:1100}") long rateLimitMs,
        @Value("${app.geocoding.cep-rate-limit-ms:80}") long cepRateLimitMs,
        @Value("${app.geocoding.cep-parallelism:8}") int cepParallelism,
        @Value("${app.geocoding.cep-batch-size:3000}") int cepBatchSize
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.importJobRepository = importJobRepository;
        this.rateLimitMs = rateLimitMs;
        this.cepRateLimitMs = cepRateLimitMs;
        this.cepParallelism = Math.max(1, cepParallelism);
        this.cepBatchSize = Math.max(500, cepBatchSize);
        this.nominatimClient = RestClient.builder()
            .baseUrl(nominatimUrl)
            .defaultHeader("User-Agent", "ProspectPortal/1.0 (B2B prospecting; contact@prospectportal.local)")
            .build();
        this.awesomeCepClient = RestClient.builder()
            .baseUrl(awesomeCepUrl)
            .build();
        this.brasilApiCepClient = RestClient.builder()
            .baseUrl(brasilApiCepUrl)
            .build();
    }

    public boolean geocodeCompany(Company company) {
        if (company.isGeocoded() && company.getLatitude() != null && company.getLongitude() != null) {
            return true;
        }

        GeocodeResult result = resolveCoordinates(
            company.getStreet(),
            company.getCity(),
            company.getState(),
            company.getZipCode()
        );
        if (result == null) {
            return false;
        }

        persistCoordinates(company.getId().toString(), result.latitude(), result.longitude(), result.precision());
        company.setLatitude(result.latitude());
        company.setLongitude(result.longitude());
        company.setGeocoded(true);
        company.setLocationPrecision(result.precision());
        return true;
    }

    public record MapCoordinate(double latitude, double longitude, String precision, boolean geocoded) {
    }

    /** Melhor coordenada disponível em cache (endereço completo ou CEP), sem chamar APIs externas. */
    public Optional<MapCoordinate> resolveForMap(
        String street,
        String city,
        String state,
        String zip,
        Double latitude,
        Double longitude,
        String precision
    ) {
        if ("EXACT".equals(precision) && latitude != null && longitude != null) {
            return Optional.of(new MapCoordinate(latitude, longitude, "EXACT", true));
        }

        if (latitude != null && longitude != null && !"UNRESOLVED".equals(precision)) {
            String resolvedPrecision = precision != null && !precision.isBlank() ? precision : "CEP";
            return Optional.of(new MapCoordinate(latitude, longitude, resolvedPrecision, true));
        }

        GeocodeResult address = lookupCache(buildCacheKey(street, city, state, zip));
        if (address != null) {
            return Optional.of(toMapCoordinate(address));
        }

        GeocodeResult cep = lookupCepCache(zip);
        if (cep != null) {
            return Optional.of(toMapCoordinate(cep));
        }

        return Optional.empty();
    }

    /** Geocodifica por endereço (Nominatim/CEP) e persiste no banco. */
    public Optional<MapCoordinate> refineWithNominatim(
        UUID companyId,
        String street,
        String city,
        String state,
        String zip
    ) throws InterruptedException {
        if (!hasGeocodableAddress(street, city)) {
            return Optional.empty();
        }

        Thread.sleep(rateLimitMs);
        GeocodeResult result = resolveCoordinates(street, city, state, zip);
        if (result == null) {
            return Optional.empty();
        }

        persistCoordinates(companyId.toString(), result.latitude(), result.longitude(), result.precision());
        return Optional.of(toMapCoordinate(result));
    }

    public boolean hasGeocodableAddress(String street, String city) {
        return street != null && !street.isBlank() && city != null && !city.isBlank();
    }

    private MapCoordinate toMapCoordinate(GeocodeResult result) {
        return new MapCoordinate(result.latitude(), result.longitude(), result.precision(), true);
    }

    public void geocodePendingCompanies(Set<String> states, ImportJob job) throws InterruptedException {
        int geocoded = geocodeBatch(states, job);
        if (geocoded > 0) {
            log.info("Geocodificadas {} empresas neste lote", geocoded);
        }
    }

    public void geocodeAllPending(Set<String> states, ImportJob job) throws InterruptedException {
        int cityApplied = applyCityCoordinatesFromMunicipios(states);
        if (cityApplied > 0) {
            log.info("Geocode CITY (SIAFI/lpad): {} empresas", cityApplied);
            updateJobProgress(job.getId(), countGeocodedCompanies());
        }
        geocodeAllPendingByCep(states, job);
        int upgraded = upgradeCityPrecisionFromCepCache(states);
        if (upgraded > 0) {
            log.info("Upgrade CITY→CEP a partir do cache: {} empresas", upgraded);
            updateJobProgress(job.getId(), countGeocodedCompanies());
        }
    }

    /** Aplica centroide municipal para pendentes, alinhando SIAFI com/sem zero à esquerda. */
    public int applyCityCoordinatesFromMunicipios(Set<String> states) {
        List<String> ordered = orderStates(states);
        if (ordered.isEmpty()) {
            return 0;
        }
        try {
            Boolean tableExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = 'geo_municipios_ibge'
                )
                """,
                Boolean.class
            );
            if (!Boolean.TRUE.equals(tableExists)) {
                log.warn("Tabela geo_municipios_ibge ausente; pulando geocode CITY");
                return 0;
            }
        } catch (Exception ex) {
            log.warn("Não foi possível verificar geo_municipios_ibge: {}", ex.getMessage());
            return 0;
        }

        String placeholders = ordered.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] args = ordered.toArray();
        return jdbcTemplate.update(
            """
            UPDATE companies c
            SET latitude = g.latitude,
                longitude = g.longitude,
                location = ST_SetSRID(ST_MakePoint(g.longitude, g.latitude), 4326)::geography,
                geocoded = TRUE,
                location_precision = 'CITY',
                updated_at = NOW()
            FROM geo_municipios_ibge g
            WHERE c.geocoded = FALSE
              AND c.data_source = 'RECEITA_FEDERAL'
              AND c.state IN (%s)
              AND c.municipality_code IS NOT NULL
              AND lpad(trim(g.siafi_id), 4, '0') = lpad(trim(c.municipality_code), 4, '0')
            """.formatted(placeholders),
            args
        );
    }

    /** Promove CITY→CEP quando o cache já tem coordenada do CEP (não marca UNRESOLVED). */
    public int upgradeCityPrecisionFromCepCache(Set<String> states) {
        List<String> ordered = orderStates(states);
        if (ordered.isEmpty()) {
            return 0;
        }
        String placeholders = ordered.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] args = ordered.toArray();
        return jdbcTemplate.update(
            """
            UPDATE companies c
            SET latitude = gc.latitude,
                longitude = gc.longitude,
                location = ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326)::geography,
                geocoded = TRUE,
                location_precision = 'CEP',
                updated_at = NOW()
            FROM geo_cache gc
            WHERE c.location_precision = 'CITY'
              AND c.data_source = 'RECEITA_FEDERAL'
              AND c.state IN (%s)
              AND c.zip_code IS NOT NULL
              AND gc.cache_key = 'cep:' || c.zip_code
            """.formatted(placeholders),
            args
        );
    }

    public void geocodeAllPendingByCep(Set<String> states, ImportJob job) throws InterruptedException {
        List<String> orderedStates = orderStates(states);
        log.info("Geocodificação rápida iniciada: {} (paralelismo={}, rate={}ms, lote={})",
            orderedStates, cepParallelism, cepRateLimitMs, cepBatchSize);

        for (String state : orderedStates) {
            geocodeStateFast(state, job.getId());
            long total = countGeocodedCompanies();
            log.info("Geocodificação rápida concluída para {} - total geocodificado: {}", state, total);
        }

        log.info("Geocodificação rápida finalizada: {} empresas", countGeocodedCompanies());
    }

    private List<String> orderStates(Set<String> states) {
        Set<String> normalized = states.stream()
            .map(s -> s.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
        List<String> ordered = new ArrayList<>();
        for (String state : GEOCODE_STATE_PRIORITY) {
            if (normalized.contains(state)) {
                ordered.add(state);
            }
        }
        normalized.stream().sorted().filter(s -> !ordered.contains(s)).forEach(ordered::add);
        return ordered;
    }

    private void geocodeStateFast(String state, UUID jobId) throws InterruptedException {
        Set<String> cachedCeps = loadCachedCepKeys();
        int batches = 0;
        String lastZip = "";

        while (true) {
            List<String> ceps = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT zip_code
                FROM companies
                WHERE geocoded = FALSE
                  AND data_source = 'RECEITA_FEDERAL'
                  AND state = ?
                  AND zip_code IS NOT NULL
                  AND length(trim(zip_code)) = 8
                  AND zip_code > ?
                ORDER BY zip_code
                LIMIT ?
                """,
                String.class,
                state,
                lastZip,
                cepBatchSize
            );
            if (ceps.isEmpty()) {
                break;
            }

            List<String> missing = ceps.stream().filter(cep -> !cachedCeps.contains(cep)).toList();
            if (!missing.isEmpty()) {
                fillCepCacheParallel(missing, cachedCeps);
            }

            batches++;
            lastZip = ceps.get(ceps.size() - 1);
            if (batches % 5 == 0) {
                updateJobProgress(jobId, countGeocodedCompanies());
                log.info("Geocodificação rápida {}: cache CEP lote {} (último CEP {})", state, batches, lastZip);
            }
        }

        log.info("Geocodificação rápida {}: aplicando coordenadas em massa...", state);
        int applied = bulkApplyFromCacheForState(state);
        int unresolved = markUnresolvedForState(state);
        updateJobProgress(jobId, countGeocodedCompanies());
        log.info("Geocodificação rápida {} concluída: {} empresas com CEP, {} sem coordenadas",
            state, applied, unresolved);
    }

    private Set<String> loadCachedCepKeys() {
        return new HashSet<>(jdbcTemplate.queryForList(
            """
            SELECT substring(cache_key from 5) AS cep
            FROM geo_cache
            WHERE cache_key LIKE 'cep:%'
            """,
            String.class
        ));
    }

    private void fillCepCacheParallel(List<String> ceps, Set<String> cachedCeps) {
        for (String cep : ceps) {
            if (cachedCeps.contains(cep)) {
                continue;
            }
            GeocodeResult result = fetchCepFromApi(cep);
            if (result != null) {
                cachedCeps.add(cep);
            }
        }
    }

    private GeocodeResult fetchCepFromApi(String cep) {
        waitCepApiSlot();
        GeocodeResult result = queryAwesomeCep(cep);
        if (result == null) {
            result = queryBrasilApiCep(cep);
        }
        if (result != null) {
            saveCepCache(cep, result);
        }
        return result;
    }

    private void waitCepApiSlot() {
        synchronized (cepApiLock) {
            long wait = nextCepApiAllowedAtMs - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            nextCepApiAllowedAtMs = System.currentTimeMillis() + cepRateLimitMs;
        }
    }

    private int bulkApplyFromCacheForState(String state) {
        return jdbcTemplate.update(
            """
            UPDATE companies c
            SET latitude = gc.latitude,
                longitude = gc.longitude,
                location = ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326)::geography,
                geocoded = TRUE,
                location_precision = 'CEP',
                updated_at = NOW()
            FROM geo_cache gc
            WHERE c.geocoded = FALSE
              AND c.data_source = 'RECEITA_FEDERAL'
              AND c.state = ?
              AND c.zip_code IS NOT NULL
              AND gc.cache_key = 'cep:' || c.zip_code
            """,
            state
        );
    }

    private int markUnresolvedForState(String state) {
        return jdbcTemplate.update(
            """
            UPDATE companies c
            SET geocoded = TRUE,
                location_precision = 'UNRESOLVED',
                updated_at = NOW()
            WHERE c.geocoded = FALSE
              AND c.data_source = 'RECEITA_FEDERAL'
              AND c.state = ?
              AND c.zip_code IS NOT NULL
              AND length(trim(c.zip_code)) = 8
              AND NOT EXISTS (
                SELECT 1 FROM geo_cache gc WHERE gc.cache_key = 'cep:' || c.zip_code
              )
            """,
            state
        );
    }

    private long countGeocodedCompanies() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM companies WHERE geocoded = TRUE",
            Long.class
        );
        return count == null ? 0 : count;
    }

    private void updateJobProgress(UUID jobId, long processed) {
        importJobRepository.findById(jobId).ifPresent(active -> {
            active.setProcessedRows(processed);
            active.setInsertedRows(processed);
            importJobRepository.saveAndFlush(active);
        });
    }

    /** Geocodificação empresa a empresa (Nominatim) - uso pontual, não em massa. */
    public void geocodeAllPendingLegacy(Set<String> states, ImportJob job) throws InterruptedException {
        int total = 0;
        int batch;
        int round = 0;
        do {
            batch = geocodeBatch(states, job);
            total += batch;
            round++;
            if (batch > 0) {
                log.info("Geocodificação lote {}: {} empresas (acumulado {})", round, batch, total);
            }
        } while (batch > 0 && round < 200);
        log.info("Geocodificação finalizada: {} empresas no total", total);
    }

    private int geocodeBatch(Set<String> states, ImportJob job) throws InterruptedException {
        List<Map<String, Object>> pending = jdbcTemplate.queryForList(
            """
            SELECT id, street, city, state, zip_code
            FROM companies
            WHERE geocoded = FALSE
              AND data_source = 'RECEITA_FEDERAL'
              AND UPPER(state) IN (%s)
            LIMIT 5000
            """.formatted(states.stream().map(s -> "'" + s.toUpperCase() + "'").reduce((a, b) -> a + "," + b).orElse("''"))
        );

        log.info("Geocodificando {} empresas (limite 5000 por execução)", pending.size());
        int geocoded = 0;

        for (Map<String, Object> row : pending) {
            Thread.sleep(rateLimitMs);
            String cacheKey = buildCacheKey(row);
            GeocodeResult cached = lookupCache(cacheKey);
            GeocodeResult result = cached;

            if (result == null) {
                result = resolveCoordinates(
                    String.valueOf(row.get("street")),
                    String.valueOf(row.get("city")),
                    String.valueOf(row.get("state")),
                    String.valueOf(row.get("zip_code"))
                );
                if (result == null) {
                    continue;
                }
                saveCache(cacheKey, result);
            }

            persistCoordinates(row.get("id").toString(), result.latitude(), result.longitude(), result.precision());
            geocoded++;
        }

        job.setInsertedRows(job.getInsertedRows() + geocoded);
        importJobRepository.save(job);
        return geocoded;
    }

    private GeocodeResult resolveCoordinates(String street, String city, String state, String zip) {
        String cacheKey = buildCacheKey(street, city, state, zip);
        GeocodeResult cached = lookupCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        GeocodeResult nominatim = queryNominatim(street, city, state, zip);
        if (nominatim != null) {
            saveCache(cacheKey, nominatim);
            return nominatim;
        }

        GeocodeResult cep = queryAwesomeCep(zip);
        if (cep == null) {
            cep = queryBrasilApiCep(zip);
        }
        if (cep != null) {
            saveCache(cacheKey, cep);
            return cep;
        }

        return null;
    }

    private void persistCoordinates(String companyId, double lat, double lng, String precision) {
        jdbcTemplate.update(
            """
            UPDATE companies
            SET latitude = ?, longitude = ?,
                location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                geocoded = TRUE,
                location_precision = ?,
                updated_at = NOW()
            WHERE id = ?::uuid
            """,
            lat, lng, lng, lat, precision, companyId
        );
    }

    private GeocodeResult lookupCepCache(String cep) {
        return lookupCache(cepCacheKey(cep));
    }

    private void saveCepCache(String cep, GeocodeResult result) {
        saveCache(cepCacheKey(cep), result);
    }

    private String cepCacheKey(String cep) {
        return "cep:" + cep;
    }

    private GeocodeResult lookupCache(String cacheKey) {
        return jdbcTemplate.query(
            """
            SELECT latitude, longitude, provider
            FROM geo_cache
            WHERE cache_key = ?
            """,
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new GeocodeResult(rs.getDouble(1), rs.getDouble(2), mapProviderPrecision(rs.getString(3)));
            },
            cacheKey
        );
    }

    private void saveCache(String cacheKey, GeocodeResult result) {
        jdbcTemplate.update(
            """
            INSERT INTO geo_cache (cache_key, latitude, longitude, provider)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (cache_key) DO NOTHING
            """,
            cacheKey,
            result.latitude(),
            result.longitude(),
            providerForPrecision(result.precision())
        );
    }

    private String buildCacheKey(Map<String, Object> row) {
        return buildCacheKey(
            String.valueOf(row.get("street")),
            String.valueOf(row.get("city")),
            String.valueOf(row.get("state")),
            String.valueOf(row.get("zip_code"))
        );
    }

    private String buildCacheKey(String street, String city, String state, String zip) {
        return (normalize(street) + "|" + normalize(city) + "|" + normalize(state) + "|" + digits(zip))
            .toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private GeocodeResult queryNominatim(String street, String city, String state, String zip) {
        try {
            ParsedStreet parsed = parseStreet(street);
            List<Map<String, Object>> result = nominatimClient.get()
                .uri(uri -> {
                    var builder = uri
                        .queryParam("format", "json")
                        .queryParam("limit", "1")
                        .queryParam("countrycodes", "br");
                    if (parsed != null && parsed.streetName() != null && parsed.number() != null) {
                        builder = builder
                            .queryParam("street", parsed.number() + " " + parsed.streetName())
                            .queryParam("city", city)
                            .queryParam("state", resolveStateName(state))
                            .queryParam("postalcode", digits(zip))
                            .queryParam("country", "Brazil");
                    } else {
                        builder = builder.queryParam("q", buildFreeformQuery(street, city, state, zip));
                    }
                    return builder.build();
                })
                .retrieve()
                .body(List.class);

            if (result == null || result.isEmpty()) {
                return null;
            }
            Map<String, Object> first = result.getFirst();
            return new GeocodeResult(
                Double.parseDouble(String.valueOf(first.get("lat"))),
                Double.parseDouble(String.valueOf(first.get("lon"))),
                "EXACT"
            );
        } catch (Exception ex) {
            log.debug("Nominatim falhou: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private GeocodeResult queryAwesomeCep(String zip) {
        String digits = digits(zip);
        if (digits.length() != 8) {
            return null;
        }
        try {
            Map<String, Object> result = awesomeCepClient.get()
                .uri("/json/" + digits)
                .retrieve()
                .body(Map.class);
            if (result == null || result.get("lat") == null || result.get("lng") == null) {
                return null;
            }
            return new GeocodeResult(
                Double.parseDouble(String.valueOf(result.get("lat"))),
                Double.parseDouble(String.valueOf(result.get("lng"))),
                "CEP"
            );
        } catch (Exception ex) {
            log.debug("AwesomeAPI CEP falhou: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private GeocodeResult queryBrasilApiCep(String zip) {
        String digits = digits(zip);
        if (digits.length() != 8) {
            return null;
        }
        try {
            Map<String, Object> result = brasilApiCepClient.get()
                .uri("/" + digits)
                .retrieve()
                .body(Map.class);
            if (result == null) {
                return null;
            }
            Object location = result.get("location");
            if (!(location instanceof Map<?, ?> locationMap)) {
                return null;
            }
            Object coordinates = locationMap.get("coordinates");
            if (!(coordinates instanceof Map<?, ?> coords)) {
                return null;
            }
            Object lat = coords.get("latitude");
            Object lng = coords.get("longitude");
            if (lat == null || lng == null || String.valueOf(lat).isBlank() || String.valueOf(lng).isBlank()) {
                return null;
            }
            return new GeocodeResult(
                Double.parseDouble(String.valueOf(lat)),
                Double.parseDouble(String.valueOf(lng)),
                "CEP"
            );
        } catch (Exception ex) {
            log.debug("BrasilAPI CEP falhou: {}", ex.getMessage());
            return null;
        }
    }

    private ParsedStreet parseStreet(String street) {
        if (street == null || street.isBlank()) {
            return null;
        }
        String normalized = street.trim().replaceAll("\\s+", " ");
        Matcher matcher = STREET_NUMBER.matcher(normalized);
        if (matcher.matches()) {
            return new ParsedStreet(matcher.group(1).trim(), matcher.group(2));
        }
        int commaIdx = normalized.lastIndexOf(',');
        if (commaIdx > 0) {
            String name = normalized.substring(0, commaIdx).trim();
            String number = normalized.substring(commaIdx + 1).trim().replaceAll("\\D", "");
            if (!name.isBlank() && !number.isBlank()) {
                return new ParsedStreet(name, number);
            }
        }
        return new ParsedStreet(normalized, null);
    }

    private String resolveStateName(String state) {
        if (state == null || state.isBlank()) {
            return "";
        }
        String key = state.trim().toUpperCase(Locale.ROOT);
        return STATE_NAMES.getOrDefault(key, state.trim());
    }

    private String buildFreeformQuery(String street, String city, String state, String zip) {
        return String.join(", ",
            nonBlank(street),
            nonBlank(city),
            nonBlank(state),
            formatCep(zip),
            "Brasil"
        ).replaceAll("(, )+", ", ").replaceAll("^, |, $", "");
    }

    private String formatCep(String zip) {
        String digits = digits(zip);
        if (digits.length() != 8) {
            return "";
        }
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? "" : value.trim();
    }

    private String normalize(String value) {
        return nonBlank(value);
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String providerForPrecision(String precision) {
        return "CEP".equals(precision) ? "CEP_API" : "NOMINATIM";
    }

    private String mapProviderPrecision(String provider) {
        if ("AWESOME_CEP".equals(provider) || "CEP_API".equals(provider) || "BRASIL_API_CEP".equals(provider)
            || "BANCO_CEPS".equals(provider)) {
            return "CEP";
        }
        return "EXACT";
    }

    private record GeocodeResult(double latitude, double longitude, String precision) {
    }

    private record ParsedStreet(String streetName, String number) {
    }
}
