package com.prospectportal.module.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prospectportal.web.dto.CnaeActivityOptionResponse;
import com.prospectportal.web.dto.CnaeSectionCatalogResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CnaeCatalogService {

    private static final TypeReference<List<SectionDefinition>> SECTIONS_TYPE = new TypeReference<>() {
    };

    private static final String KIND_SECTION = "SECTION";
    private static final String KIND_DIVISION = "DIVISION";
    private static final String KIND_SUBCLASS = "SUBCLASS";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private volatile List<CnaeSectionCatalogResponse> cachedCatalog;

    public CnaeCatalogService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<CnaeSectionCatalogResponse> getCatalog() {
        List<CnaeSectionCatalogResponse> catalog = cachedCatalog;
        if (catalog != null) {
            return catalog;
        }

        synchronized (this) {
            if (cachedCatalog == null) {
                cachedCatalog = buildCatalog();
            }
            return cachedCatalog;
        }
    }

    public List<CnaeActivityOptionResponse> searchSubclasses(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        String term = query.trim().toLowerCase(Locale.ROOT);
        String like = "%" + term + "%";

        try {
            return jdbcTemplate.query(
                """
                    SELECT code, description
                    FROM rf_cnaes
                    WHERE LENGTH(code) >= 7
                      AND code ~ '^[0-9]+$'
                      AND (LOWER(code) LIKE ? OR LOWER(description) LIKE ?)
                    ORDER BY code
                    LIMIT ?
                    """,
                (rs, rowNum) -> toSubclassOption(rs.getString("code"), rs.getString("description")),
                like,
                like,
                safeLimit
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<CnaeActivityOptionResponse> listSubclassesByPrefix(String prefix, int limit) {
        if (prefix == null || !prefix.matches("\\d{2,6}")) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 500);
        String likePrefix = prefix + "%";

        try {
            return jdbcTemplate.query(
                """
                    SELECT code, description
                    FROM rf_cnaes
                    WHERE LENGTH(code) >= 7
                      AND code ~ '^[0-9]+$'
                      AND code LIKE ?
                    ORDER BY code
                    LIMIT ?
                    """,
                (rs, rowNum) -> toSubclassOption(rs.getString("code"), rs.getString("description")),
                likePrefix,
                safeLimit
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    public void invalidateCache() {
        cachedCatalog = null;
    }

    private List<CnaeSectionCatalogResponse> buildCatalog() {
        Map<String, String> rfDescriptions = loadRfDescriptions();

        return loadSectionDefinitions().stream()
            .map(section -> toCatalogResponse(section, rfDescriptions))
            .toList();
    }

    private Map<String, String> loadRfDescriptions() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rf_cnaes", Long.class);
            if (count == null || count == 0L) {
                return Map.of();
            }

            return jdbcTemplate.query(
                "SELECT code, description FROM rf_cnaes ORDER BY code",
                rs -> {
                    Map<String, String> descriptions = new LinkedHashMap<>();
                    while (rs.next()) {
                        descriptions.put(rs.getString("code"), rs.getString("description"));
                    }
                    return descriptions;
                }
            );
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<SectionDefinition> loadSectionDefinitions() {
        try (InputStream input = new ClassPathResource("cnae/cnae-sections.json").getInputStream()) {
            return objectMapper.readValue(input, SECTIONS_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load CNAE section metadata", ex);
        }
    }

    private CnaeSectionCatalogResponse toCatalogResponse(SectionDefinition section, Map<String, String> rfDescriptions) {
        String sectionFilterValue = section.divisions().stream()
            .map(DivisionDefinition::code)
            .collect(Collectors.joining(","));

        List<CnaeActivityOptionResponse> activities = new ArrayList<>();
        activities.add(new CnaeActivityOptionResponse(
            section.code(),
            "Toda a seção " + section.code(),
            sectionFilterValue,
            KIND_SECTION
        ));

        for (DivisionDefinition division : section.divisions()) {
            String divisionLabel = resolveDivisionLabel(division.code(), division.label(), rfDescriptions);
            activities.add(new CnaeActivityOptionResponse(
                division.code(),
                division.code() + " — " + divisionLabel,
                division.code(),
                KIND_DIVISION
            ));
        }

        return new CnaeSectionCatalogResponse(
            section.code(),
            section.title(),
            section.searchHint(),
            sectionFilterValue,
            activities
        );
    }

    private CnaeActivityOptionResponse toSubclassOption(String code, String description) {
        return new CnaeActivityOptionResponse(
            code,
            code + " — " + description,
            code,
            KIND_SUBCLASS
        );
    }

    private String resolveDivisionLabel(String divisionCode, String fallback, Map<String, String> rfDescriptions) {
        String exact = rfDescriptions.get(divisionCode);
        if (exact != null && !exact.isBlank()) {
            return exact;
        }

        return rfDescriptions.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(divisionCode))
            .min(Comparator.comparingInt(entry -> entry.getKey().length()))
            .map(Map.Entry::getValue)
            .orElse(fallback);
    }

    private record SectionDefinition(
        String code,
        String title,
        String searchHint,
        List<DivisionDefinition> divisions
    ) {
    }

    private record DivisionDefinition(String code, String label) {
    }
}
