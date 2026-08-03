package com.prospectportal.module.ingestion.rf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prospectportal.module.discovery.CnaeCatalogService;
import com.prospectportal.module.ingestion.dto.RfImportRequest;
import com.prospectportal.module.ingestion.entity.ImportJob;
import com.prospectportal.module.ingestion.repository.ImportJobRepository;
import com.prospectportal.module.search.CompanyIndexingService;
import com.prospectportal.module.geo.GeocodingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class RfImportService {

    private static final Logger log = LoggerFactory.getLogger(RfImportService.class);
    private static final DateTimeFormatter RF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;
    private final ImportJobRepository importJobRepository;
    private final ObjectMapper objectMapper;
    private final GeocodingService geocodingService;
    private final CompanyIndexingService indexingService;
    private final CnaeCatalogService cnaeCatalogService;
    private final Path extractedRoot;
    private final int chunkSize;

    public RfImportService(
        JdbcTemplate jdbcTemplate,
        ImportJobRepository importJobRepository,
        ObjectMapper objectMapper,
        GeocodingService geocodingService,
        CompanyIndexingService indexingService,
        CnaeCatalogService cnaeCatalogService,
        @Value("${app.data.rf-extracted}") String extractedPath,
        @Value("${app.import.chunk-size:2000}") int chunkSize
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.importJobRepository = importJobRepository;
        this.objectMapper = objectMapper;
        this.geocodingService = geocodingService;
        this.indexingService = indexingService;
        this.cnaeCatalogService = cnaeCatalogService;
        this.extractedRoot = Path.of(extractedPath);
        this.chunkSize = chunkSize;
    }

    @Transactional
    public ImportJob startImport(RfImportRequest request) {
        importJobRepository.findFirstByStatusOrderByCreatedAtDesc("RUNNING").ifPresent(stale -> {
            stale.setStatus("FAILED");
            stale.setErrorMessage("Substituído por nova importação (retomada)");
            stale.setFinishedAt(Instant.now());
            importJobRepository.save(stale);
        });

        ImportJob job = new ImportJob();
        job.setJobType("RF_CNPJ");
        job.setStatus("PENDING");
        try {
            job.setParamsJson(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            job.setParamsJson("{}");
        }
        job.setProcessedRows(0);
        job.setInsertedRows(0);
        job.setSkippedRows(0);
        job.setCreatedAt(Instant.now());
        return importJobRepository.save(job);
    }

    @Transactional
    public ImportJob startEnrichment(java.util.List<String> states, boolean geocode, boolean syncElasticsearch) {
        importJobRepository.findFirstByStatusOrderByCreatedAtDesc("RUNNING").ifPresent(stale -> {
            stale.setStatus("FAILED");
            stale.setErrorMessage("Substituído por novo job de enriquecimento");
            stale.setFinishedAt(Instant.now());
            importJobRepository.save(stale);
        });

        ImportJob job = new ImportJob();
        job.setJobType("RF_ENRICH");
        job.setStatus("PENDING");
        job.setParamsJson("{\"states\":" + states + ",\"geocode\":" + geocode + ",\"syncElasticsearch\":" + syncElasticsearch + "}");
        job.setProcessedRows(0);
        job.setInsertedRows(0);
        job.setSkippedRows(0);
        job.setCreatedAt(Instant.now());
        return importJobRepository.save(job);
    }

    @Async("importTaskExecutor")
    public void runEnrichmentAsync(UUID jobId, java.util.List<String> states, boolean geocode, boolean syncElasticsearch) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        importJobRepository.saveAndFlush(job);

        Set<String> stateSet = new HashSet<>(states.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList());

        try {
            if (geocode) {
                geocodingService.geocodeAllPending(stateSet, job);
            }
            if (syncElasticsearch) {
                indexingService.reindexByStates(stateSet, job);
            }
            job.setStatus("COMPLETED");
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
            log.info("Enriquecimento concluído: {} geocodificadas/indexadas", job.getInsertedRows());
        } catch (Exception ex) {
            log.error("Falha no enriquecimento", ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
        }
    }

    @Async("importTaskExecutor")
    public void runImportAsync(UUID jobId, RfImportRequest request) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        importJobRepository.save(job);

        Set<String> states = new HashSet<>(request.states().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList());

        try {
            loadMunicipios(job);
            loadCnaes(job);

            if (request.loadEmpresas()) {
                loadAllEmpresas(job);
            } else {
                ensureEmpresasStagingReady();
            }

            importEstabelecimentos(job, states, request.estabelecimentoFiles());

            if (request.geocodeAfterImport()) {
                geocodingService.geocodeAllPending(states, job);
            }

            if (request.syncElasticsearch()) {
                indexingService.reindexByStates(states, job);
            }

            job.setStatus("COMPLETED");
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
            log.info("Importação RF concluída: {} inseridas, {} ignoradas", job.getInsertedRows(), job.getSkippedRows());
        } catch (Exception ex) {
            log.error("Falha na importação RF", ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
        }
    }

    private void loadMunicipios(ImportJob job) throws Exception {
        Path file = RfCsvPaths.findExtractedFile(extractedRoot, "Municipios", "MUNIC");
        jdbcTemplate.update("TRUNCATE rf_municipios");
        try (BufferedReader reader = Files.newBufferedReader(file, RfCsvPaths.RF_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(";", -1);
                String code = RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, 0));
                String name = RfCsvPaths.field(cols, 1);
                if (code.isBlank() || name.isBlank()) {
                    continue;
                }
                jdbcTemplate.update("INSERT INTO rf_municipios (code, name) VALUES (?, ?) ON CONFLICT DO NOTHING", code, name);
            }
        }
        log.info("Municípios RF carregados");
    }

    private void loadCnaes(ImportJob job) throws Exception {
        Path file = RfCsvPaths.findExtractedFile(extractedRoot, "Cnaes", "CNAE");
        jdbcTemplate.update("TRUNCATE rf_cnaes");
        try (BufferedReader reader = Files.newBufferedReader(file, RfCsvPaths.RF_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(";", -1);
                String code = RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, 0));
                String description = RfCsvPaths.field(cols, 1);
                if (code.isBlank() || description.isBlank()) {
                    continue;
                }
                jdbcTemplate.update("INSERT INTO rf_cnaes (code, description) VALUES (?, ?) ON CONFLICT DO NOTHING", code, description);
            }
        }
        log.info("CNAEs RF carregados");
        cnaeCatalogService.invalidateCache();
    }

    private void loadAllEmpresas(ImportJob job) throws Exception {
        Long existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rf_empresas", Long.class);
        long skipRemaining = existing != null ? existing : 0L;
        if (skipRemaining > 0) {
            log.info("Retomando carga de empresas: {} já em rf_empresas", skipRemaining);
            job.setProcessedRows(skipRemaining);
            importJobRepository.save(job);
        } else {
            jdbcTemplate.update("TRUNCATE rf_empresas");
        }

        List<Path> folders;
        try (Stream<Path> stream = Files.list(extractedRoot)) {
            folders = stream
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("Empresas"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }

        for (Path folder : folders) {
            skipRemaining = loadEmpresasFile(folder, job, skipRemaining);
        }
    }

    private long loadEmpresasFile(Path folder, ImportJob job, long skipRemaining) {
        try {
            Path file = Files.list(folder)
                .filter(p -> p.getFileName().toString().toUpperCase().contains("EMPRECSV"))
                .findFirst()
                .orElseThrow();
            log.info("Carregando empresas: {}", file);
            try (BufferedReader reader = Files.newBufferedReader(file, RfCsvPaths.RF_CHARSET)) {
                String line;
                int batch = 0;
                while ((line = reader.readLine()) != null) {
                    String[] cols = line.split(";", -1);
                    String basico = padLeft(RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, 0)), 8);
                    if (basico.length() != 8) {
                        continue;
                    }
                    if (skipRemaining > 0) {
                        skipRemaining--;
                        continue;
                    }
                    String legalName = RfCsvPaths.field(cols, 1);
                    String nature = normalizeRfCode(RfCsvPaths.field(cols, 2), 20);
                    BigDecimal capital = parseCapital(RfCsvPaths.field(cols, 4));
                    String porte = normalizeRfCode(RfCsvPaths.field(cols, 5), 20);
                    jdbcTemplate.update(
                        """
                        INSERT INTO rf_empresas (cnpj_basico, legal_name, legal_nature_code, capital_social, company_size_code)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (cnpj_basico) DO UPDATE SET
                          legal_name = EXCLUDED.legal_name,
                          legal_nature_code = EXCLUDED.legal_nature_code,
                          capital_social = EXCLUDED.capital_social,
                          company_size_code = EXCLUDED.company_size_code,
                          loaded_at = NOW()
                        """,
                        basico, legalName, nature, capital, porte
                    );
                    batch++;
                    if (batch % chunkSize == 0) {
                        job.setProcessedRows(job.getProcessedRows() + chunkSize);
                        importJobRepository.save(job);
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Erro ao carregar " + folder + ": " + ex.getMessage(), ex);
        }
        return skipRemaining;
    }

    private void importEstabelecimentos(ImportJob job, Set<String> states, List<String> fileFilters) throws Exception {
        List<Path> folders;
        try (Stream<Path> stream = Files.list(extractedRoot)) {
            folders = stream
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("Estabelecimentos"))
                .filter(path -> fileFilters.isEmpty() || fileFilters.stream().anyMatch(f -> path.getFileName().toString().contains(f)))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
        for (Path folder : folders) {
            importEstabelecimentosFolder(folder, job, states);
        }
    }

    private void importEstabelecimentosFolder(Path folder, ImportJob job, Set<String> states) {
        try {
            Path file = Files.list(folder)
                .filter(p -> p.getFileName().toString().toUpperCase().contains("ESTABELE"))
                .findFirst()
                .orElseThrow();
            log.info("Importando estabelecimentos: {} (UFs: {})", file, states);
            try (BufferedReader reader = Files.newBufferedReader(file, RfCsvPaths.RF_CHARSET)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    job.setProcessedRows(job.getProcessedRows() + 1);
                    String[] cols = line.split(";", -1);

                    String situacao = RfCsvPaths.field(cols, 5);
                    if (!"02".equals(situacao)) {
                        job.setSkippedRows(job.getSkippedRows() + 1);
                        continue;
                    }

                    RfEstabelecimentoFields fields = RfEstabelecimentoFields.parse(cols);
                    String uf = fields.uf();
                    if (!states.contains(uf)) {
                        job.setSkippedRows(job.getSkippedRows() + 1);
                        continue;
                    }

                    String basico = padLeft(RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, 0)), 8);
                    String ordem = padLeft(RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, 1)), 4);
                    String dv = padLeft(RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, 2)), 2);
                    String cnpj = basico + ordem + dv;
                    if (cnpj.length() != 14) {
                        job.setSkippedRows(job.getSkippedRows() + 1);
                        continue;
                    }

                    var empresa = jdbcTemplate.query(
                        "SELECT legal_name, legal_nature_code, capital_social, company_size_code FROM rf_empresas WHERE cnpj_basico = ?",
                        rs -> rs.next()
                            ? new EmpresaRow(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getBigDecimal(3),
                                rs.getString(4)
                            )
                            : null,
                        basico
                    );

                    if (empresa == null) {
                        job.setSkippedRows(job.getSkippedRows() + 1);
                        continue;
                    }

                    String municipioCode = fields.municipioCode();
                    String city = jdbcTemplate.query(
                        "SELECT name FROM rf_municipios WHERE code = ?",
                        rs -> rs.next() ? rs.getString(1) : "Desconhecido",
                        municipioCode
                    );

                    String cnae = RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, 11));
                    String cnaeSecondary = normalizeCnaeSecondary(RfCsvPaths.field(cols, 12));
                    String cnaeDesc = jdbcTemplate.query(
                        "SELECT description FROM rf_cnaes WHERE code = ?",
                        rs -> rs.next() ? rs.getString(1) : null,
                        cnae
                    );

                    String tradeName = RfCsvPaths.field(cols, 4);
                    String tipoLog = RfCsvPaths.field(cols, 13);
                    String logradouro = RfCsvPaths.field(cols, 14);
                    String numero = RfCsvPaths.field(cols, 15);
                    String bairro = fields.bairro();
                    String cep = fields.cep();
                    String email = RfCsvPaths.field(cols, 27);
                    String phone = fields.phone();
                    LocalDate openedAt = parseDate(RfCsvPaths.field(cols, 10));
                    String street = Stream.of(tipoLog, logradouro, numero).filter(s -> !s.isBlank()).reduce((a, b) -> a + " " + b).orElse(logradouro);
                    String revenue = mapPorte(empresa.companySizeCode(), empresa.capitalSocial());

                    int updated = jdbcTemplate.update(
                        """
                        INSERT INTO companies (
                          cnpj, legal_name, trade_name, cnae_main, cnae_secondary, cnae_description, legal_nature,
                          capital_social, opened_at, city, state, neighborhood, street, zip_code,
                          estimated_revenue, email, phone, municipality_code, registration_status,
                          company_size_code, data_source, geocoded, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEITA_FEDERAL', FALSE, NOW(), NOW())
                        ON CONFLICT (cnpj) DO UPDATE SET
                          legal_name = EXCLUDED.legal_name,
                          trade_name = EXCLUDED.trade_name,
                          cnae_main = EXCLUDED.cnae_main,
                          cnae_secondary = EXCLUDED.cnae_secondary,
                          cnae_description = EXCLUDED.cnae_description,
                          capital_social = EXCLUDED.capital_social,
                          opened_at = EXCLUDED.opened_at,
                          city = EXCLUDED.city,
                          state = EXCLUDED.state,
                          neighborhood = EXCLUDED.neighborhood,
                          street = EXCLUDED.street,
                          zip_code = EXCLUDED.zip_code,
                          estimated_revenue = EXCLUDED.estimated_revenue,
                          email = EXCLUDED.email,
                          phone = EXCLUDED.phone,
                          municipality_code = EXCLUDED.municipality_code,
                          registration_status = EXCLUDED.registration_status,
                          company_size_code = EXCLUDED.company_size_code,
                          data_source = 'RECEITA_FEDERAL',
                          updated_at = NOW()
                        """,
                        cnpj,
                        empresa.legalName(),
                        tradeName.isBlank() ? null : tradeName,
                        cnae,
                        cnaeSecondary,
                        cnaeDesc,
                        empresa.legalNatureCode(),
                        empresa.capitalSocial(),
                        openedAt,
                        city,
                        uf,
                        blankToNull(bairro),
                        blankToNull(street),
                        blankToNull(cep),
                        revenue,
                        blankToNull(email),
                        phone,
                        blankToNull(municipioCode),
                        situacao,
                        empresa.companySizeCode()
                    );

                    if (updated > 0) {
                        job.setInsertedRows(job.getInsertedRows() + 1);
                    }

                    if (job.getProcessedRows() % chunkSize == 0) {
                        importJobRepository.save(job);
                    }
                }
            }
            importJobRepository.save(job);
        } catch (Exception ex) {
            throw new IllegalStateException("Erro ao importar estabelecimentos " + folder + ": " + ex.getMessage(), ex);
        }
    }

    private record EmpresaRow(String legalName, String legalNatureCode, BigDecimal capitalSocial, String companySizeCode) {
    }

    private BigDecimal parseCapital(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.replace(",", "."));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("0")) {
            return null;
        }
        try {
            return LocalDate.parse(raw, RF_DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeRfCode(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        String value = !digits.isEmpty() ? digits : trimmed;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private String normalizePorte(String raw) {
        return normalizeRfCode(raw, 20);
    }

    private String mapPorte(String porte, BigDecimal capital) {
        String code = normalizePorte(porte);
        if (code.length() > 2) {
            code = code.substring(0, 2);
        }
        if ("01".equals(code)) {
            return "SMALL";
        }
        if ("03".equals(code)) {
            return "MEDIUM";
        }
        if ("05".equals(code)) {
            return capital != null && capital.compareTo(new BigDecimal("5000000")) >= 0 ? "LARGE" : "MEDIUM";
        }
        if (capital != null && capital.compareTo(new BigDecimal("5000000")) >= 0) {
            return "LARGE";
        }
        if (capital != null && capital.compareTo(new BigDecimal("360000")) >= 0) {
            return "MEDIUM";
        }
        return "SMALL";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String padLeft(String value, int size) {
        if (value.isBlank()) {
            value = "0";
        }
        if (value.length() >= size) {
            return value.substring(value.length() - size);
        }
        return "0".repeat(size - value.length()) + value;
    }

    private void ensureEmpresasStagingReady() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rf_empresas", Long.class);
        long loaded = count == null ? 0L : count;
        if (loaded < 50_000_000L) {
            throw new IllegalStateException(
                "rf_empresas incompleto (" + loaded + " registros). "
                    + "Execute a importação com loadEmpresas=true antes de reimportar estabelecimentos."
            );
        }
        log.info("rf_empresas pronto para lookup: {} registros", loaded);
    }

    private String normalizeCnaeSecondary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .map(RfCsvPaths::onlyDigits)
            .filter(code -> !code.isBlank())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        return normalized.isBlank() ? null : normalized;
    }
}
