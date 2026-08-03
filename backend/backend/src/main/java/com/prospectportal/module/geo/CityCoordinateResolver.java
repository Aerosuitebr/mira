package com.prospectportal.module.geo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CityCoordinateResolver {

    private static final Map<String, double[]> STATE_CENTERS = Map.ofEntries(
        Map.entry("RJ", new double[] { -22.9068, -43.1729 }),
        Map.entry("SP", new double[] { -23.5505, -46.6333 }),
        Map.entry("MG", new double[] { -19.9167, -43.9345 }),
        Map.entry("ES", new double[] { -20.3155, -40.3128 }),
        Map.entry("GO", new double[] { -16.6869, -49.2648 }),
        Map.entry("DF", new double[] { -15.7939, -47.8828 }),
        Map.entry("MT", new double[] { -15.6014, -56.0979 }),
        Map.entry("MS", new double[] { -20.4697, -54.6201 })
    );

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, double[]> memoryCache = new ConcurrentHashMap<>();

    public CityCoordinateResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public double[] resolveForMap(Double latitude, Double longitude, String zipCode, String city, String state) {
        if (latitude != null && longitude != null) {
            return new double[] { latitude, longitude };
        }
        if (zipCode != null && !zipCode.isBlank()) {
            double[] cepCoords = lookupCepCache(zipCode);
            if (cepCoords != null) {
                return cepCoords;
            }
        }
        if (city == null || city.isBlank() || state == null || state.isBlank()) {
            return stateCenter(state);
        }

        String stateKey = state.trim().toUpperCase(Locale.ROOT);
        String cityKey = city.trim().toLowerCase(Locale.ROOT);
        String cacheKey = stateKey + "|" + cityKey;

        return memoryCache.computeIfAbsent(cacheKey, key -> {
            double[] cached = lookupGeoCache(cityKey, stateKey);
            if (cached != null) {
                return cached;
            }
            double[] center = stateCenter(stateKey);
            return center != null ? center : new double[] { -22.9068, -43.1729 };
        });
    }

    private double[] lookupGeoCache(String city, String state) {
        String geoKey = "city|" + city + "|" + state;
        return jdbcTemplate.query(
            "SELECT latitude, longitude FROM geo_cache WHERE cache_key = ?",
            rs -> rs.next() ? new double[] { rs.getDouble(1), rs.getDouble(2) } : null,
            geoKey
        );
    }

    private double[] lookupCepCache(String zipCode) {
        String digits = zipCode.replaceAll("\\D", "");
        if (digits.length() != 8) {
            return null;
        }
        return jdbcTemplate.query(
            "SELECT latitude, longitude FROM geo_cache WHERE cache_key = ?",
            rs -> rs.next() ? new double[] { rs.getDouble(1), rs.getDouble(2) } : null,
            "cep:" + digits
        );
    }

    private double[] stateCenter(String state) {
        if (state == null) {
            return null;
        }
        return STATE_CENTERS.get(state.toUpperCase(Locale.ROOT));
    }
}
