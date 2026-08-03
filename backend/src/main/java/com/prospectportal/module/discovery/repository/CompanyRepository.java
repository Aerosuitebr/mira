package com.prospectportal.module.discovery.repository;

import com.prospectportal.module.discovery.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByCnpj(String cnpj);

    List<Company> findByDataSourceOrderByTradeNameAsc(String dataSource);

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE (:keyword = '' OR LOWER(c.legal_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(c.trade_name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR c.cnpj LIKE CONCAT('%', :keyword, '%'))
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND :state != '' AND c.cnae_main >= :cnaeValue AND c.cnae_main < :cnaePrefixUpper)
            OR (:cnaeMode = 'prefix' AND :state = '' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND (:state = '' OR c.state = :state)
          AND (:city = '' OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:revenue = '' OR c.estimated_revenue = :revenue)
          AND (:activeOnly = FALSE OR COALESCE(c.registration_status, '02') = '02')
          AND (:contactableOnly = FALSE OR c.web_contactable = TRUE)
        """,
        countQuery = """
        SELECT COUNT(*) FROM companies c
        WHERE (:keyword = '' OR LOWER(c.legal_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(c.trade_name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR c.cnpj LIKE CONCAT('%', :keyword, '%'))
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND :state != '' AND c.cnae_main >= :cnaeValue AND c.cnae_main < :cnaePrefixUpper)
            OR (:cnaeMode = 'prefix' AND :state = '' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND (:state = '' OR c.state = :state)
          AND (:city = '' OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:revenue = '' OR c.estimated_revenue = :revenue)
          AND (:activeOnly = FALSE OR COALESCE(c.registration_status, '02') = '02')
          AND (:contactableOnly = FALSE OR c.web_contactable = TRUE)
        """,
        nativeQuery = true)
    Page<Company> search(
        @Param("keyword") String keyword,
        @Param("cnaeMode") String cnaeMode,
        @Param("cnaeValue") String cnaeValue,
        @Param("cnaePrefixUpper") String cnaePrefixUpper,
        @Param("cnaePrefixes") List<String> cnaePrefixes,
        @Param("state") String state,
        @Param("city") String city,
        @Param("revenue") String revenue,
        @Param("activeOnly") boolean activeOnly,
        @Param("contactableOnly") boolean contactableOnly,
        Pageable pageable
    );

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE c.id IN (
            SELECT id FROM companies
            WHERE legal_name ILIKE CONCAT(:namePrefix, '%')
            LIMIT 500
        )
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND (:city = '' OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:revenue = '' OR c.estimated_revenue = :revenue)
          AND (:activeOnly = FALSE OR COALESCE(c.registration_status, '02') = '02')
          AND (:contactableOnly = FALSE OR c.web_contactable = TRUE)
        ORDER BY c.legal_name
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true)
    List<Company> searchNationalByLegalNamePrefix(
        @Param("namePrefix") String namePrefix,
        @Param("cnaeMode") String cnaeMode,
        @Param("cnaeValue") String cnaeValue,
        @Param("cnaePrefixes") List<String> cnaePrefixes,
        @Param("city") String city,
        @Param("revenue") String revenue,
        @Param("activeOnly") boolean activeOnly,
        @Param("contactableOnly") boolean contactableOnly,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE c.id IN (
            SELECT id FROM companies
            WHERE legal_name ILIKE CONCAT('%', :nameTerm, '%')
            LIMIT 500
        )
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND (:city = '' OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:revenue = '' OR c.estimated_revenue = :revenue)
          AND (:activeOnly = FALSE OR COALESCE(c.registration_status, '02') = '02')
          AND (:contactableOnly = FALSE OR c.web_contactable = TRUE)
        ORDER BY c.legal_name
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true)
    List<Company> searchNationalByLegalNameContains(
        @Param("nameTerm") String nameTerm,
        @Param("cnaeMode") String cnaeMode,
        @Param("cnaeValue") String cnaeValue,
        @Param("cnaePrefixes") List<String> cnaePrefixes,
        @Param("city") String city,
        @Param("revenue") String revenue,
        @Param("activeOnly") boolean activeOnly,
        @Param("contactableOnly") boolean contactableOnly,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE c.id IN (
            SELECT id FROM companies
            WHERE trade_name IS NOT NULL
              AND trade_name ILIKE CONCAT('%', :nameTerm, '%')
            LIMIT 500
        )
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND (:city = '' OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:revenue = '' OR c.estimated_revenue = :revenue)
          AND (:activeOnly = FALSE OR COALESCE(c.registration_status, '02') = '02')
          AND (:contactableOnly = FALSE OR c.web_contactable = TRUE)
        ORDER BY c.trade_name
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true)
    List<Company> searchNationalByTradeNameContains(
        @Param("nameTerm") String nameTerm,
        @Param("cnaeMode") String cnaeMode,
        @Param("cnaeValue") String cnaeValue,
        @Param("cnaePrefixes") List<String> cnaePrefixes,
        @Param("city") String city,
        @Param("revenue") String revenue,
        @Param("activeOnly") boolean activeOnly,
        @Param("contactableOnly") boolean contactableOnly,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE c.id IN (
            SELECT id FROM companies
            WHERE trade_name IS NOT NULL
              AND trade_name ILIKE CONCAT(:namePrefix, '%')
            LIMIT 500
        )
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND (:city = '' OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:revenue = '' OR c.estimated_revenue = :revenue)
          AND (:activeOnly = FALSE OR COALESCE(c.registration_status, '02') = '02')
          AND (:contactableOnly = FALSE OR c.web_contactable = TRUE)
        ORDER BY c.trade_name
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true)
    List<Company> searchNationalByTradeNamePrefix(
        @Param("namePrefix") String namePrefix,
        @Param("cnaeMode") String cnaeMode,
        @Param("cnaeValue") String cnaeValue,
        @Param("cnaePrefixes") List<String> cnaePrefixes,
        @Param("city") String city,
        @Param("revenue") String revenue,
        @Param("activeOnly") boolean activeOnly,
        @Param("contactableOnly") boolean contactableOnly,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE c.cnpj LIKE CONCAT(:cnpjPrefix, '%')
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND (:city = '' OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:revenue = '' OR c.estimated_revenue = :revenue)
          AND (:activeOnly = FALSE OR COALESCE(c.registration_status, '02') = '02')
          AND (:contactableOnly = FALSE OR c.web_contactable = TRUE)
        ORDER BY
          CASE WHEN c.cnpj = :cnpjPrefix THEN 0 ELSE 1 END,
          c.cnpj
        LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true)
    List<Company> searchNationalByCnpjPrefix(
        @Param("cnpjPrefix") String cnpjPrefix,
        @Param("cnaeMode") String cnaeMode,
        @Param("cnaeValue") String cnaeValue,
        @Param("cnaePrefixes") List<String> cnaePrefixes,
        @Param("city") String city,
        @Param("revenue") String revenue,
        @Param("activeOnly") boolean activeOnly,
        @Param("contactableOnly") boolean contactableOnly,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE LOWER(c.legal_name) LIKE LOWER(CONCAT('%', :term, '%'))
           OR LOWER(COALESCE(c.trade_name, '')) LIKE LOWER(CONCAT('%', :term, '%'))
           OR c.cnpj LIKE CONCAT('%', :digits, '%')
        ORDER BY COALESCE(c.trade_name, c.legal_name)
        LIMIT :limit
        """, nativeQuery = true)
    List<Company> searchForRecipients(
        @Param("term") String term,
        @Param("digits") String digits,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT c.* FROM companies c
        WHERE c.latitude IS NOT NULL
          AND c.longitude IS NOT NULL
          AND ST_DWithin(
            c.location,
            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
            :radiusMeters
          )
          AND (
            :cnaeMode = 'none'
            OR (:cnaeMode = 'exact' AND (c.cnae_main = :cnaeValue OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'prefix' AND (c.cnae_main LIKE CONCAT(:cnaeValue, '%') OR c.cnae_secondary LIKE CONCAT('%', :cnaeValue, '%')))
            OR (:cnaeMode = 'sections' AND (LEFT(c.cnae_main, 2) IN (:cnaePrefixes) OR EXISTS (
                SELECT 1 FROM unnest(string_to_array(c.cnae_secondary, ',')) AS sec(code)
                WHERE LEFT(sec.code, 2) IN (:cnaePrefixes)
            )))
          )
          AND COALESCE(c.registration_status, '02') = '02'
        ORDER BY ST_Distance(c.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)
        LIMIT :limit
        """, nativeQuery = true)
    List<Company> findWithinRadius(
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusMeters") double radiusMeters,
        @Param("cnaeMode") String cnaeMode,
        @Param("cnaeValue") String cnaeValue,
        @Param("cnaePrefixes") List<String> cnaePrefixes,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT c.id, c.cnpj, c.legal_name, c.trade_name, c.cnae_main, c.cnae_secondary, c.cnae_description,
               c.legal_nature, c.capital_social, c.opened_at, c.city, c.state, c.neighborhood,
               c.street, c.zip_code, c.latitude, c.longitude, c.location_precision, c.estimated_revenue, c.website,
               c.registration_status, c.company_size_code, c.email, c.phone, c.municipality_code,
               c.data_source, c.geocoded, c.web_contactable, c.web_probe_status, c.web_probed_at, c.created_at, c.updated_at
        FROM companies c
        WHERE c.opened_at IS NOT NULL
        ORDER BY c.opened_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Company> findNewestOpeningsNative(@Param("limit") int limit);
}
