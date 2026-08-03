package com.prospectportal.module.ingestion.repository;

import com.prospectportal.module.ingestion.entity.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    Optional<ImportJob> findFirstByStatusOrderByCreatedAtDesc(String status);
    List<ImportJob> findTop10ByOrderByCreatedAtDesc();
}
