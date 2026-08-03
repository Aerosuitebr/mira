package com.prospectportal.module.search;

import com.prospectportal.module.ingestion.entity.ImportJob;
import com.prospectportal.module.ingestion.repository.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class CompanyIndexingService {

    private static final Logger log = LoggerFactory.getLogger(CompanyIndexingService.class);
    private static final int BATCH_SIZE = 10_000;

    private final JdbcTemplate jdbcTemplate;
    private final CompanySearchRepository searchRepository;
    private final ImportJobRepository importJobRepository;
    private final ElasticsearchOperations operations;
    private final String indexName;

    public CompanyIndexingService(
        JdbcTemplate jdbcTemplate,
        CompanySearchRepository searchRepository,
        ImportJobRepository importJobRepository,
        ElasticsearchOperations operations,
        @Value("${app.elasticsearch.index}") String indexName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.searchRepository = searchRepository;
        this.importJobRepository = importJobRepository;
        this.operations = operations;
        this.indexName = indexName;
    }

    public void ensureIndex() {
        IndexOperations indexOps = operations.indexOps(CompanyDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
            log.info("Índice Elasticsearch '{}' criado", indexName);
        }
    }

    public void recreateIndex() {
        IndexOperations indexOps = operations.indexOps(CompanyDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
            log.info("Índice Elasticsearch '{}' removido", indexName);
        }
        indexOps.createWithMapping();
        log.info("Índice Elasticsearch '{}' recriado", indexName);
    }

    public long countIndexedDocuments() {
        if (!operations.indexOps(CompanyDocument.class).exists()) {
            return 0;
        }
        return operations.count(
            NativeQuery.builder().withQuery(q -> q.matchAll(m -> m)).build(),
            CompanyDocument.class,
            IndexCoordinates.of(indexName)
        );
    }

    public long countPostgresCompanies() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companies", Long.class);
        return count == null ? 0 : count;
    }

    public int indexByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        ensureIndex();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?::uuid"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, cnpj, legal_name, trade_name, cnae_main, cnae_secondary, cnae_description,
                   city, state, estimated_revenue, data_source, latitude, longitude,
                   registration_status, web_contactable
            FROM companies
            WHERE id IN (%s)
            """.formatted(placeholders),
            ids.toArray()
        );
        if (rows.isEmpty()) {
            return 0;
        }

        List<CompanyDocument> batch = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            batch.add(toDocument(row));
        }
        searchRepository.saveAll(batch);
        return batch.size();
    }

    public long reindexByStates(Set<String> states, ImportJob job) {
        ensureIndex();
        long total = 0;
        for (String state : states.stream().sorted().toList()) {
            long stateTotal = reindexState(state.toUpperCase(Locale.ROOT), job, total);
            total += stateTotal;
            log.info("Elasticsearch: {} empresas indexadas em {}", stateTotal, state);
        }
        log.info("Elasticsearch: {} documentos indexados no total", total);
        return total;
    }

    private long reindexState(String state, ImportJob job, long processedBefore) {
        long stateIndexed = 0;
        UUID lastId = null;

        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, cnpj, legal_name, trade_name, cnae_main, cnae_secondary, cnae_description,
                       city, state, estimated_revenue, data_source, latitude, longitude,
                       registration_status, web_contactable
                FROM companies
                WHERE state = ?
                  AND (?::uuid IS NULL OR id > ?::uuid)
                ORDER BY id
                LIMIT ?
                """,
                state,
                lastId,
                lastId,
                BATCH_SIZE
            );

            if (rows.isEmpty()) {
                break;
            }

            List<CompanyDocument> batch = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                batch.add(toDocument(row));
            }
            searchRepository.saveAll(batch);

            lastId = UUID.fromString(rows.get(rows.size() - 1).get("id").toString());
            stateIndexed += rows.size();

            long processed = processedBefore + stateIndexed;
            job.setProcessedRows(processed);
            job.setInsertedRows(processed);
            importJobRepository.save(job);
        }

        return stateIndexed;
    }

    private CompanyDocument toDocument(Map<String, Object> row) {
        CompanyDocument doc = new CompanyDocument();
        doc.setId(row.get("id").toString());
        doc.setCnpj(String.valueOf(row.get("cnpj")));
        doc.setLegalName(String.valueOf(row.get("legal_name")));
        Object trade = row.get("trade_name");
        doc.setTradeName(trade == null ? null : trade.toString());
        doc.setCnaeMain(String.valueOf(row.get("cnae_main")));
        Object cnaeSecondary = row.get("cnae_secondary");
        doc.setCnaeSecondary(cnaeSecondary == null ? null : cnaeSecondary.toString());
        Object cnaeDesc = row.get("cnae_description");
        doc.setCnaeDescription(cnaeDesc == null ? null : cnaeDesc.toString());
        doc.setCity(String.valueOf(row.get("city")));
        doc.setState(String.valueOf(row.get("state")));
        Object revenue = row.get("estimated_revenue");
        doc.setEstimatedRevenue(revenue == null ? null : revenue.toString());
        doc.setDataSource(String.valueOf(row.get("data_source")));
        Object registrationStatus = row.get("registration_status");
        doc.setRegistrationStatus(registrationStatus == null ? "02" : registrationStatus.toString());
        doc.setWebContactable(Boolean.TRUE.equals(row.get("web_contactable")));
        Object lat = row.get("latitude");
        Object lng = row.get("longitude");
        if (lat != null && lng != null) {
            doc.setLocation(new GeoPoint(((Number) lat).doubleValue(), ((Number) lng).doubleValue()));
        }
        return doc;
    }
}
