package com.prospectportal.module.proposal.repository;

import com.prospectportal.module.proposal.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

    List<Proposal> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Proposal> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
        SELECT p FROM Proposal p
        LEFT JOIN FETCH p.lead l
        LEFT JOIN FETCH l.company
        WHERE p.approvalToken = :token
        """)
    Optional<Proposal> findByApprovalToken(@Param("token") UUID token);
}
