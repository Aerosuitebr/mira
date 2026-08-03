package com.prospectportal.module.project.repository;

import com.prospectportal.module.project.entity.ProjectMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {

    List<ProjectMilestone> findByProjectIdOrderByPositionAsc(UUID projectId);
}
