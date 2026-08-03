package com.prospectportal.web.dto;

import java.util.List;
import java.util.UUID;

public record CreateLeadsRequest(List<UUID> companyIds) {
}
