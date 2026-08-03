package com.prospectportal.module.crm.repository;

import com.prospectportal.module.crm.entity.CrmCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrmCardRepository extends JpaRepository<CrmCard, UUID> {
    List<CrmCard> findByPipelineIdOrderByStageIdAscPositionAsc(UUID pipelineId);

    Optional<CrmCard> findByLeadId(UUID leadId);

    @Query("""
        SELECT c FROM CrmCard c
        JOIN FETCH c.lead l
        LEFT JOIN FETCH l.owner
        JOIN FETCH l.company
        JOIN FETCH c.stage
        WHERE c.pipeline.id = :pipelineId
        ORDER BY c.stage.position, c.position
        """)
    List<CrmCard> findBoardCards(@Param("pipelineId") UUID pipelineId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("""
        UPDATE CrmCard c
        SET c.stage.id = :stageId, c.position = :position, c.updatedAt = :updatedAt
        WHERE c.id = :cardId
        """)
    int updateStageAndPosition(
        @Param("cardId") UUID cardId,
        @Param("stageId") UUID stageId,
        @Param("position") int position,
        @Param("updatedAt") java.time.Instant updatedAt
    );
}
