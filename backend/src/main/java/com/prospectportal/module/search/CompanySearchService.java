package com.prospectportal.module.search;

import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.GeoDistanceType;
import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class CompanySearchService {

    private static final long INDEX_POPULATED_TTL_MS = 60_000L;
    private static final int TRACK_TOTAL_HITS_UP_TO = 10_000;

    private final ElasticsearchOperations operations;
    private final AtomicBoolean indexPopulated = new AtomicBoolean(false);
    private final AtomicLong indexPopulatedCheckedAtMs = new AtomicLong(0);

    public CompanySearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public boolean isIndexPopulated() {
        long now = System.currentTimeMillis();
        long checkedAt = indexPopulatedCheckedAtMs.get();
        if (checkedAt > 0 && (now - checkedAt) < INDEX_POPULATED_TTL_MS) {
            return indexPopulated.get();
        }
        boolean populated = checkIndexPopulated();
        indexPopulated.set(populated);
        indexPopulatedCheckedAtMs.set(now);
        return populated;
    }

    private boolean checkIndexPopulated() {
        try {
            if (!operations.indexOps(CompanyDocument.class).exists()) {
                return false;
            }
            // Evita count do índice inteiro (16M+): um hit basta.
            SearchHits<CompanyDocument> probe = operations.search(
                NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .withMaxResults(1)
                    .withTrackTotalHits(false)
                    .build(),
                CompanyDocument.class
            );
            return probe.hasSearchHits();
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
                .fields("legalName^3", "tradeName^3", "cnpj^2", "cnaeDescription")
            )));
        }
        if (cnae != null && !cnae.isBlank()) {
            applyCnaeFilter(bool, CnaeFilter.parse(cnae));
        }
        if (state != null && !state.isBlank()) {
            bool.filter(Query.of(q -> q.term(t -> t.field("state").value(state.toUpperCase(Locale.ROOT)))));
        }
        if (city != null && !city.isBlank()) {
            String cityValue = city.trim().toUpperCase(Locale.ROOT);
            // Keyword field: prefixo sem leading wildcard (bem mais rápido que *city*).
            bool.filter(Query.of(q -> q.bool(b -> b
                .should(Query.of(s -> s.term(t -> t.field("city").value(cityValue))))
                .should(Query.of(s -> s.prefix(p -> p.field("city").value(cityValue))))
                .should(Query.of(s -> s.wildcard(w -> w.field("city").value(cityValue + "*").caseInsensitive(true))))
                .minimumShouldMatch("1")
            )));
        }
        if (revenue != null && !revenue.isBlank()) {
            bool.filter(Query.of(q -> q.term(t -> t.field("estimatedRevenue").value(revenue))));
        }

        boolean broad = keyword == null || keyword.isBlank();
        int trackUpTo = broad ? 1_000 : TRACK_TOTAL_HITS_UP_TO;

        NativeQuery query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(bool.build())))
            .withPageable(PageRequest.of(page, size))
            .withTrackTotalHitsUpTo(trackUpTo)
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

    public List<CompanyResponse> searchByRadius(
        double latitude,
        double longitude,
        double radiusKm,
        String cnae,
        int limit
    ) {
        int cappedLimit = Math.min(Math.max(limit, 1), 500);
        GeoLocation center = GeoLocation.of(loc -> loc.latlon(ll -> ll.lat(latitude).lon(longitude)));

        BoolQuery.Builder bool = new BoolQuery.Builder();
        bool.filter(Query.of(q -> q.term(t -> t.field("registrationStatus").value("02"))));
        bool.filter(Query.of(q -> q.geoDistance(g -> g
            .field("location")
            .distance(radiusKm + "km")
            .location(center)
        )));
        if (cnae != null && !cnae.isBlank()) {
            applyCnaeFilter(bool, CnaeFilter.parse(cnae));
        }

        NativeQuery query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(bool.build())))
            .withSort(SortOptions.of(s -> s.geoDistance(gd -> gd
                .field("location")
                .location(center)
                .order(SortOrder.Asc)
                .unit(DistanceUnit.Kilometers)
                .distanceType(GeoDistanceType.Arc)
            )))
            .withMaxResults(cappedLimit)
            .withTrackTotalHits(false)
            .build();

        SearchHits<CompanyDocument> hits = operations.search(query, CompanyDocument.class);
        return hits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .map(this::toResponse)
            .toList();
    }

    private void applyCnaeFilter(BoolQuery.Builder bool, CnaeFilter filter) {
        switch (filter.mode()) {
            case CnaeFilter.MODE_EXACT -> bool.filter(cnaeMatchQuery(
                Query.of(q -> q.term(t -> t.field("cnaeMain").value(filter.value()))),
                Query.of(q -> q.wildcard(w -> w.field("cnaeSecondary").value("*" + filter.value() + "*")))
            ));
            case CnaeFilter.MODE_PREFIX ->
                // Só cnaeMain com prefix: wildcard em cnaeSecondary (*47*) derruba a busca em 16M docs.
                bool.filter(Query.of(q -> q.prefix(p -> p.field("cnaeMain").value(filter.value()))));
            case CnaeFilter.MODE_SECTIONS -> {
                BoolQuery.Builder sectionBool = new BoolQuery.Builder();
                filter.prefixes().forEach(prefix ->
                    sectionBool.should(Query.of(q -> q.prefix(p -> p.field("cnaeMain").value(prefix))))
                );
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
        boolean hasLocation = doc.getLocation() != null;
        String precision = doc.getLocationPrecision();
        if ((precision == null || precision.isBlank()) && hasLocation) {
            precision = "CEP";
        }
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
            doc.getNeighborhood(),
            doc.getStreet(),
            doc.getZipCode(),
            BigDecimal.ZERO,
            null,
            doc.getEstimatedRevenue(),
            null,
            null,
            null,
            hasLocation ? doc.getLocation().getLat() : null,
            hasLocation ? doc.getLocation().getLon() : null,
            hasLocation,
            precision,
            doc.isWebContactable()
        );
    }
}
