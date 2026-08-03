package com.prospectportal.module.proposal.repository;

import com.prospectportal.module.proposal.entity.ProposalItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposalItemRepository extends JpaRepository<ProposalItem, UUID> {

    List<ProposalItem> findByProposalIdOrderBySortOrderAsc(UUID proposalId);

    void deleteByProposalId(UUID proposalId);
}
