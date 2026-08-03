package com.prospectportal.module.discovery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TradeNameSearchGuard {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean cachedReady = new AtomicBoolean(false);
    private final AtomicReference<Instant> checkedAt = new AtomicReference<>(Instant.EPOCH);

    public TradeNameSearchGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isTradeNameIndexReady() {
        Instant lastCheck = checkedAt.get();
        if (lastCheck.isAfter(Instant.now().minusSeconds(30))) {
            return cachedReady.get();
        }

        boolean ready = Boolean.TRUE.equals(jdbcTemplate.query(
            """
            SELECT indisvalid
            FROM pg_index
            WHERE indexrelid = 'idx_companies_trade_name_trgm'::regclass
            """,
            rs -> rs.next() && rs.getBoolean(1)
        ));
        cachedReady.set(ready);
        checkedAt.set(Instant.now());
        return ready;
    }
}
