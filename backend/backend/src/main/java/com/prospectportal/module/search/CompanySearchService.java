package com.prospectportal.module.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.prospectportal.module.discovery.CnaeFilter;
import com.prospectportal.web.dto.CompanyResponse;
import com.prospectportal.web.dto.PageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class CompanySearchService {

    private final ElasticsearchOperations operations;

    public CompanySearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public boolean isIndexPopulated() {
        try {
            if (!operations.indexOps(CompanyDocument.class).exists()) {
                return false;
            }
            return operations.count(
                NativeQuery.builder().withQuery(q -> q.matchAll(m -> m)).build(),
                CompanyDocument.class
            ) > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public PageResponse<CompanyResponse> search(
        String keyword,
        String cnae,
        String state,
        String city,
        String revenue,
        boolean activeOnly,
        int page,
        int size
    ) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (activeOnly) {
            bool.filter(Query.of(q -> q.term(t -> t.field("registrationStatus").value("02"))));
        }

        if (keyword != null && !keyword.isBlank()) {
            bool.must(Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .fields("legalName", "tradeName", "cnpj", "cnaeDescription")
            )));
        }
        if (cnae != null && !cnae.isBlank()) {
            applyCnaeFilter(bool, CnaeFilter.parse(cnae));
        }
        if (state != null && !state.isBlank()) {
            bool.filter(Query.of(q -> q.term(t -> t.field("state").value(state.toUpperCase()))));
        }
        if (city != null && !city.isBlank()) {
            bool.filter(Query.of(q -> q.wildcard(w -> w.field("city").value("*" + city.toLowerCase() + "*"))));
        }
        if (revenue != null && !revenue.isBlank()) {
            bool.filter(Query.of(q -> q.term(t -> t.field("estimatedRevenue").value(revenue))));
        }

        NativeQuery query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(bool.build())))
            .withPageable(PageRequest.of(page, size))
            .build();

        SearchHits<CompanyDocument> hits = operations.search(query, CompanyDocument.class);
        List<CompanyResponse> content = hits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .map(this::toResponse)
            .toList();

        long total = hits.getTotalHits();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, total, totalPages, page, size);
    }

    private void applyCnaeFilter(BoolQuery.Builder bool, CnaeFilter filter) {
        switch (filter.mode()) {
            case CnaeFilter.MODE_EXACT -> bool.filter(cnaeMatchQuery(
                Query.of(q -> q.term(t -> t.field("cnaeMain").value(filter.value()))),
                Query.of(q -> q.wildcard(w -> w.field("cnaeSecondary").value("*" + filter.value() + "*")))
            ));
            case CnaeFilter.MODE_PREFIX -> {
                // Alinha ao Postgres com UF: prioriza CNAE principal no prefixo (evita aéreas
                // que só listam oficina/MRO como CNAE secundário).
                if (filter.value().length() >= 5) {
                    bool.filter(Query.of(q -> q.prefix(p -> p.field("cnaeMain").value(filter.value()))));
                } else {
                    bool.filter(cnaeMatchQuery(
                        Query.of(q -> q.prefix(p -> p.field("cnaeMain").value(filter.value()))),
                        Query.of(q -> q.wildcard(w -> w.field("cnaeSecondary").value("*" + filter.value() + "*")))
                    ));
                }
            }
            case CnaeFilter.MODE_SECTIONS -> {
                BoolQuery.Builder sectionBool = new BoolQuery.Builder();
                filter.prefixes().forEach(prefix -> {
                    sectionBool.should(Query.of(q -> q.prefix(p -> p.field("cnaeMain").value(prefix))));
                    sectionBool.should(Query.of(q -> q.wildcard(w -> w.field("cnaeSecondary").value("*" + prefix + "*"))));
                });
                sectionBool.minimumShouldMatch("1");
                bool.filter(Query.of(q -> q.bool(sectionBool.build())));
            }
            default -> {
            }
        }
    }

    private Query cnaeMatchQuery(Query mainMatch, Query secondaryMatch) {
        BoolQuery.Builder cnaeBool = new BoolQuery.Builder();
        cnaeBool.should(mainMatch);
        cnaeBool.should(secondaryMatch);
        cnaeBool.minimumShouldMatch("1");
        return Query.of(q -> q.bool(cnaeBool.build()));
    }

    private CompanyResponse toResponse(CompanyDocument doc) {
        return new CompanyResponse(
            java.util.UUID.fromString(doc.getId()),
            doc.getCnpj(),
            doc.getLegalName(),
            doc.getTradeName(),
            doc.getCnaeMain(),
            doc.getCnaeSecondary(),
            doc.getCnaeDescription(),
            doc.getCity(),
            doc.getState(),
            null,
            null,
            null,
            BigDecimal.ZERO,
            null,
            doc.getEstimatedRevenue(),
            null,
            null,
            null,
            doc.getLocation() != null ? doc.getLocation().getLat() : null,
            doc.getLocation() != null ? doc.getLocation().getLon() : null,
            false,
            null,
            doc.isWebContactable()
        );
    }
}
