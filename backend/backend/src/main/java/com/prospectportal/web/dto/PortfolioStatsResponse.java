package com.prospectportal.web.dto;

import java.math.BigDecimal;

public record PortfolioStatsResponse(
    long total,
    boolean empty,
    long activeCount,
    BigDecimal totalLtv,
    BigDecimal avgLtv
) {
}
