package com.prospectportal.module.project.repository;

import com.prospectportal.module.project.entity.ProjectDeliverable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectDeliverableRepository extends JpaRepository<ProjectDeliverable, UUID> {

    List<ProjectDeliverable> findByProjectIdAndVisibleToClientTrueOrderByUploadedAtDesc(UUID projectId);
}
