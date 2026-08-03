package com.prospectportal.web.dto;

import java.math.BigDecimal;

public record CreateClientRequest(
    String legalName,
    String tradeName,
    String document,
    String email,
    String phone,
    String city,
    String state,
    BigDecimal initialValue
) {
}
