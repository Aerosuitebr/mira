package com.prospectportal.web.dto;

public record ApproveProposalRequest(
    String signerName,
    String signerDocument
) {
}
