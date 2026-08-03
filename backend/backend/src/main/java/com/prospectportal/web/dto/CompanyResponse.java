package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CompanyResponse(
    UUID id,
    String cnpj,
    String legalName,
    String tradeName,
    String cnaeMain,
    String cnaeSecondary,
    String cnaeDescription,
    String city,
    String state,
    String neighborhood,
    String street,
    String zipCode,
    BigDecimal capitalSocial,
    LocalDate openedAt,
    String estimatedRevenue,
    String website,
    String email,
    String phone,
    Double latitude,
    Double longitude,
    boolean geocoded,
    String locationPrecision,
    boolean webContactable
) {
}
