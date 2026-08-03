package com.prospectportal.module.professional;

import com.prospectportal.web.dto.ProfessionalResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.prospectportal.security.AuthContext;

import java.util.List;
import java.util.Optional;

@Service
public class ProfessionalDirectoryService {
    private final JdbcTemplate jdbc;
    private final AuthContext authContext;

    public ProfessionalDirectoryService(JdbcTemplate jdbc, AuthContext authContext) {
        this.jdbc = jdbc;
        this.authContext = authContext;
    }

    public record SearchPoint(double latitude, double longitude, String label) {}

    /** Resolve o centro da busca apenas com coordenadas já armazenadas no MIRA. */
    public Optional<SearchPoint> resolveSearchPoint(String location) {
        String term = location == null ? "" : location.trim();
        if (term.length() < 3) return Optional.empty();
        List<SearchPoint> points = jdbc.query("""
            SELECT latitude, longitude, label FROM (
                SELECT latitude, longitude, CONCAT(COALESCE(neighborhood, city), ', ', state) AS label, 0 AS priority
                  FROM professional_listings
                 WHERE available = TRUE AND (neighborhood ILIKE CONCAT('%', ?, '%') OR city ILIKE CONCAT('%', ?, '%'))
                UNION ALL
                SELECT latitude, longitude, CONCAT(COALESCE(neighborhood, city), ', ', state) AS label, 1 AS priority
                  FROM companies
                 WHERE latitude IS NOT NULL AND longitude IS NOT NULL
                   AND (neighborhood ILIKE CONCAT('%', ?, '%') OR city ILIKE CONCAT('%', ?, '%') OR zip_code = regexp_replace(?, '\\D', '', 'g'))
            ) locations ORDER BY priority LIMIT 1
            """, (rs, row) -> new SearchPoint(rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getString("label")),
            term, term, term, term, term);
        return points.stream().findFirst();
    }

    public List<ProfessionalResponse> search(String query, double latitude, double longitude, double radiusKm) {
        double safeRadius = Math.min(Math.max(radiusKm, 1), 50);
        int limit = safeRadius <= 10 ? 10 : 20;
        String term = query == null ? "" : query.trim();
        return jdbc.query("""
            SELECT id, name, occupation, specialties, bio, email, whatsapp, phone, website, instagram,
                   profile_image_url, rating, review_count, years_experience, verified, service_mode,
                   neighborhood, city, state, latitude, longitude, source,
                   ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000.0 AS distance_km
              FROM professional_listings
             WHERE available = TRUE
               AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
               AND (? = '' OR to_tsvector('portuguese', occupation || ' ' || COALESCE(specialties, '') || ' ' || COALESCE(bio, ''))
                    @@ websearch_to_tsquery('portuguese', ?))
             ORDER BY distance_km ASC, verified DESC, rating DESC NULLS LAST
             LIMIT ?
            """, (rs, row) -> new ProfessionalResponse(
                rs.getObject("id", java.util.UUID.class), rs.getString("name"), rs.getString("occupation"),
                rs.getString("specialties"), rs.getString("bio"), null, null, null,
                rs.getString("email") != null && !rs.getString("email").isBlank(),
                rs.getString("whatsapp") != null && !rs.getString("whatsapp").isBlank(),
                rs.getString("phone") != null && !rs.getString("phone").isBlank(),
                rs.getString("website"), rs.getString("instagram"),
                rs.getString("profile_image_url"), (Double) rs.getObject("rating"), rs.getInt("review_count"),
                (Integer) rs.getObject("years_experience"), rs.getBoolean("verified"), rs.getString("service_mode"),
                rs.getString("neighborhood"), rs.getString("city"), rs.getString("state"),
                rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getDouble("distance_km"), rs.getString("source")
            ), longitude, latitude, longitude, latitude, safeRadius * 1000, term, term, limit);
    }

    public record ContactResult(String channel, String value, boolean freeContactConsumed) {}

    @Transactional
    public ContactResult claimContact(java.util.UUID professionalId, String requestedChannel) {
        String channel = requestedChannel == null ? "" : requestedChannel.trim().toUpperCase();
        if (!channel.equals("WHATSAPP") && !channel.equals("EMAIL") && !channel.equals("PHONE")) {
            throw new IllegalArgumentException("Canal inválido.");
        }
        var tenantId = authContext.tenantId();
        int inserted = jdbc.update("""
            INSERT INTO free_mira_contact_usage (tenant_id, target_type, target_id, channel)
            VALUES (?, 'PROFESSIONAL', ?, ?) ON CONFLICT (tenant_id) DO NOTHING
            """, tenantId, professionalId.toString(), channel);
        var usage = jdbc.queryForMap("SELECT target_type, target_id, channel FROM free_mira_contact_usage WHERE tenant_id = ?", tenantId);
        if (!"PROFESSIONAL".equals(usage.get("target_type")) || !professionalId.toString().equals(usage.get("target_id")) || !channel.equals(usage.get("channel"))) {
            throw new IllegalStateException("Seu contato gratuito do MIRA já foi utilizado.");
        }
        String column = channel.equals("WHATSAPP") ? "whatsapp" : channel.equals("PHONE") ? "phone" : "email";
        List<String> values = jdbc.queryForList("SELECT " + column + " FROM professional_listings WHERE id = ? AND available = TRUE", String.class, professionalId);
        String value = values.stream().filter(v -> v != null && !v.isBlank()).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Este profissional não disponibilizou esse canal."));
        return new ContactResult(channel, value, inserted > 0);
    }
}
