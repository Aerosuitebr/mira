package com.prospectportal.module.enrichment.registry;

import java.util.List;

public record RegistryRefreshResult(
    boolean applied,
    String source,
    List<RegistryPartner> partners
) {
    public static RegistryRefreshResult skipped() {
        return new RegistryRefreshResult(false, null, List.of());
    }

    public static RegistryRefreshResult failed() {
        return new RegistryRefreshResult(false, null, List.of());
    }

    public static RegistryRefreshResult success(String source, List<RegistryPartner> partners) {
        return new RegistryRefreshResult(true, source, partners);
    }
}
