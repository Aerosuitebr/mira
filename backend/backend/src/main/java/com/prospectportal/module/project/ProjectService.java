package com.prospectportal.module.project;

import com.prospectportal.module.client.entity.Client;
import com.prospectportal.common.entity.Tenant;
import com.prospectportal.module.portal.entity.PortalAccessToken;
import com.prospectportal.module.portal.repository.PortalAccessTokenRepository;
import com.prospectportal.module.project.entity.Project;
import com.prospectportal.module.project.entity.ProjectDeliverable;
import com.prospectportal.module.project.entity.ProjectMilestone;
import com.prospectportal.module.project.repository.ProjectDeliverableRepository;
import com.prospectportal.module.project.repository.ProjectMilestoneRepository;
import com.prospectportal.module.project.repository.ProjectRepository;
import com.prospectportal.module.proposal.entity.Proposal;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.ProjectBoardResponse;
import com.prospectportal.web.dto.ProjectColumnResponse;
import com.prospectportal.web.dto.ProjectMilestoneResponse;
import com.prospectportal.web.dto.ProjectResponse;
import com.prospectportal.web.dto.PublicPortalResponse;
import com.prospectportal.web.dto.PublicTimelineItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProjectService {

    private static final List<String> DELIVERY_COLUMNS = List.of(
        "NOT_STARTED", "IN_PROGRESS", "REVIEW", "COMPLETED"
    );

    private final AuthContext authContext;
    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectDeliverableRepository deliverableRepository;
    private final PortalAccessTokenRepository portalAccessTokenRepository;
    private final String publicBaseUrl;

    public ProjectService(
        AuthContext authContext,
        ProjectRepository projectRepository,
        ProjectMilestoneRepository milestoneRepository,
        ProjectDeliverableRepository deliverableRepository,
        PortalAccessTokenRepository portalAccessTokenRepository,
        @Value("${app.public-base-url:http://localhost:4201}") String publicBaseUrl
    ) {
        this.authContext = authContext;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.deliverableRepository = deliverableRepository;
        this.portalAccessTokenRepository = portalAccessTokenRepository;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Transactional
    public void bootstrapFromProposal(Proposal proposal, Client client) {
        if (projectRepository.findByTenantIdOrderByUpdatedAtDesc(proposal.getTenant().getId()).stream()
            .anyMatch(p -> p.getProposal() != null && p.getProposal().getId().equals(proposal.getId()))) {
            return;
        }

        Instant now = Instant.now();
        Instant dueAt = proposal.getExpiresAt() != null
            ? proposal.getExpiresAt()
            : now.plus(30, ChronoUnit.DAYS);

        Project project = new Project();
        project.setTenant(proposal.getTenant());
        project.setClient(client);
        project.setProposal(proposal);
        project.setName(proposal.getTitle());
        project.setStatus("NOT_STARTED");
        project.setProgressPercent(0);
        project.setDueAt(dueAt);
        project.setOwner(proposal.getCreatedBy());
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.save(project);

        createDefaultMilestones(project);
        createPortalToken(proposal.getTenant(), project.getId());
    }

    @Transactional(readOnly = true)
    public ProjectBoardResponse getDeliveryBoard() {
        UUID tenantId = authContext.tenantId();
        List<Project> projects = projectRepository.findBoardProjects(tenantId);

        Map<String, List<ProjectResponse>> grouped = new LinkedHashMap<>();
        for (String status : DELIVERY_COLUMNS) {
            grouped.put(status, new ArrayList<>());
        }

        for (Project project : projects) {
            grouped.computeIfAbsent(project.getStatus(), key -> new ArrayList<>())
                .add(toProjectResponse(project));
        }

        List<ProjectColumnResponse> columns = grouped.entrySet().stream()
            .map(entry -> new ProjectColumnResponse(entry.getKey(), labelForStatus(entry.getKey()), entry.getValue()))
            .toList();

        return new ProjectBoardResponse(columns);
    }

    @Transactional
    public ProjectResponse updateStatus(UUID projectId, String status) {
        Project project = projectRepository.findByTenantIdAndId(authContext.tenantId(), projectId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Projeto não encontrado"));
        project.setStatus(status);
        project.setUpdatedAt(Instant.now());
        if ("IN_PROGRESS".equals(status) && project.getStartedAt() == null) {
            project.setStartedAt(Instant.now());
        }
        if ("COMPLETED".equals(status)) {
            project.setCompletedAt(Instant.now());
            project.setProgressPercent(100);
        }
        return toProjectResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public PublicPortalResponse getPublicPortal(UUID token) {
        PortalAccessToken access = portalAccessTokenRepository.findByToken(token)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Portal não encontrado"));

        if (access.getExpiresAt() != null && Instant.now().isAfter(access.getExpiresAt())) {
            throw new ResponseStatusException(NOT_FOUND, "Link expirado");
        }

        if (!"PROJECT".equals(access.getResourceType())) {
            throw new ResponseStatusException(NOT_FOUND, "Portal inválido");
        }

        Project project = projectRepository.findWithClient(access.getResourceId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Projeto não encontrado"));

        List<PublicTimelineItem> timeline = milestoneRepository.findByProjectIdOrderByPositionAsc(project.getId())
            .stream()
            .map(m -> new PublicTimelineItem(
                m.getTitle(),
                m.getDescription(),
                m.getStatus(),
                m.getCompletedAt(),
                m.isRequiresClientApproval(),
                m.getClientApprovedAt()
            ))
            .toList();

        List<String> files = deliverableRepository.findByProjectIdAndVisibleToClientTrueOrderByUploadedAtDesc(project.getId())
            .stream()
            .map(ProjectDeliverable::getFileName)
            .toList();

        return new PublicPortalResponse(
            project.getClient().getTradeName() != null ? project.getClient().getTradeName() : project.getClient().getLegalName(),
            project.getName(),
            project.getStatus(),
            project.getProgressPercent(),
            timeline,
            files
        );
    }

    private void createDefaultMilestones(Project project) {
        String[] titles = {"Kickoff", "Execução", "Revisão", "Entrega final"};
        Instant now = Instant.now();
        for (int i = 0; i < titles.length; i++) {
            ProjectMilestone milestone = new ProjectMilestone();
            milestone.setProject(project);
            milestone.setTitle(titles[i]);
            milestone.setStatus(i == 0 ? "IN_PROGRESS" : "WAITING");
            milestone.setPosition(i);
            milestone.setRequiresClientApproval(i == 2);
            milestone.setCreatedAt(now);
            milestone.setUpdatedAt(now);
            milestoneRepository.save(milestone);
        }
    }

    private void createPortalToken(Tenant tenant, UUID projectId) {
        PortalAccessToken token = new PortalAccessToken();
        token.setTenant(tenant);
        token.setToken(UUID.randomUUID());
        token.setResourceType("PROJECT");
        token.setResourceId(projectId);
        token.setCreatedAt(Instant.now());
        portalAccessTokenRepository.save(token);
    }

    private ProjectResponse toProjectResponse(Project project) {
        List<ProjectMilestone> milestoneEntities = milestoneRepository.findByProjectIdOrderByPositionAsc(project.getId());
        List<ProjectMilestoneResponse> milestones = milestoneEntities.stream()
            .map(m -> new ProjectMilestoneResponse(
                m.getId(),
                m.getTitle(),
                m.getStatus(),
                m.getPosition(),
                m.isRequiresClientApproval()
            ))
            .toList();

        int progressPercent = computeMilestoneProgress(milestoneEntities, project.getProgressPercent());

        return new ProjectResponse(
            project.getId(),
            project.getName(),
            project.getStatus(),
            progressPercent,
            project.getClient().getTradeName() != null ? project.getClient().getTradeName() : project.getClient().getLegalName(),
            project.getDueAt(),
            milestones
        );
    }

    private int computeMilestoneProgress(List<ProjectMilestone> milestones, int fallback) {
        if (milestones.isEmpty()) {
            return fallback;
        }
        long completed = milestones.stream().filter(this::isMilestoneCompleted).count();
        return (int) Math.round((completed * 100.0) / milestones.size());
    }

    private boolean isMilestoneCompleted(ProjectMilestone milestone) {
        if (milestone.getCompletedAt() != null) {
            return true;
        }
        String status = milestone.getStatus();
        return "COMPLETED".equals(status) || "DONE".equals(status);
    }

    private String labelForStatus(String status) {
        return switch (status) {
            case "NOT_STARTED" -> "A Iniciar";
            case "IN_PROGRESS" -> "Em Execução";
            case "REVIEW" -> "Revisão";
            case "COMPLETED" -> "Concluído";
            default -> status;
        };
    }
}
