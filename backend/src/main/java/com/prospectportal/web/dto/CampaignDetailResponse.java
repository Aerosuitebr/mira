package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignDetailResponse(
    UUID id, String name, String channel, String status, Instant createdAt,
    String followUpBody, long total, long queued, long sent, long waitingReply,
    long replied, long awaitingApproval, long step2Queued, long step2Sent,
    long failed, long skipped, List<CampaignMessageDetail> messages
) {}
