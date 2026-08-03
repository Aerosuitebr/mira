package com.prospectportal.module.client;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.entity.User;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.common.repository.UserRepository;
import com.prospectportal.module.client.entity.Client;
import com.prospectportal.module.client.repository.ClientRepository;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.outreach.entity.OutreachMessage;
import com.prospectportal.module.outreach.repository.OutreachMessageRepository;
import com.prospectportal.module.proposal.entity.Proposal;
import com.prospectportal.module.proposal.repository.ProposalRepository;
import com.prospectportal.module.project.entity.Project;
import com.prospectportal.module.project.repository.ProjectRepository;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.Client360Response;
import com.prospectportal.web.dto.ClientContactHistoryItem;
import com.prospectportal.web.dto.ClientListItemResponse;
import com.prospectportal.web.dto.ClientProposalHistoryItem;
import com.prospectportal.web.dto.ClientServiceItem;
import com.prospectportal.web.dto.CreateClientRequest;
import com.prospectportal.web.dto.PortfolioStatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ClientService {

    private final AuthContext authContext;
    private final ClientRepository clientRepository;
    private final CompanyRepository companyRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ProposalRepository proposalRepository;
    private final ProjectRepository projectRepository;
    private final OutreachMessageRepository outreachMessageRepository;

    public ClientService(
        AuthContext authContext,
        ClientRepository clientRepository,
        CompanyRepository companyRepository,
        TenantRepository tenantRepository,
        UserRepository userRepository,
        ProposalRepository proposalRepository,
        ProjectRepository projectRepository,
        OutreachMessageRepository outreachMessageRepository
    ) {
        this.authContext = authContext;
        this.clientRepository = clientRepository;
        this.companyRepository = companyRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.proposalRepository = proposalRepository;
        this.projectRepository = projectRepository;
        this.outreachMessageRepository = outreachMessageRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioStatsResponse portfolioStats() {
        UUID tenantId = authContext.tenantId();
        List<Client> clients = clientRepository.findPortfolio(
            tenantId, null, null, null, null, Instant.EPOCH
        );
        long total = clients.size();
        long activeCount = clients.stream()
            .filter(client -> "ACTIVE".equalsIgnoreCase(client.getStatus()))
            .count();
        BigDecimal totalLtv = clients.stream()
            .map(Client::getLifetimeValue)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgLtv = total == 0
            ? BigDecimal.ZERO
            : totalLtv.divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
        return new PortfolioStatsResponse(total, total == 0, activeCount, totalLtv, avgLtv);
    }

    @Transactional
    public ClientListItemResponse createManual(CreateClientRequest request) {
        String legalName = trimToNull(request.legalName());
        String document = normalizeCnpj(request.document());
        String city = trimToNull(request.city());
        String state = normalizeState(request.state());

        if (legalName == null || document == null || city == null || state == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Razão social, CNPJ, cidade e UF são obrigatórios");
        }
        if (document.length() != 14) {
            throw new ResponseStatusException(BAD_REQUEST, "CNPJ deve conter 14 dígitos");
        }

        UUID tenantId = authContext.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant não encontrado"));
        User owner = userRepository.findById(authContext.userId()).orElse(null);

        Company company = companyRepository.findByCnpj(document)
            .orElseGet(() -> createManualCompany(request, document, legalName, city, state));

        if (clientRepository.findByTenantIdAndCompanyId(tenantId, company.getId()).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "Cliente já cadastrado na carteira");
        }

        BigDecimal initialValue = request.initialValue() != null ? request.initialValue() : BigDecimal.ZERO;
        Instant now = Instant.now();

        Client client = new Client();
        client.setTenant(tenant);
        client.setCompany(company);
        client.setOwner(owner);
        client.setLegalName(legalName);
        client.setTradeName(trimToNull(request.tradeName()));
        client.setDocument(document);
        client.setEmail(trimToNull(request.email()));
        client.setPhone(trimToNull(request.phone()));
        client.setCity(city);
        client.setState(state);
        client.setStatus("ACTIVE");
        client.setServiceStatus("NONE");
        client.setLifetimeValue(initialValue.max(BigDecimal.ZERO));
        client.setContractedAt(now);
        client.setCreatedAt(now);
        client.setUpdatedAt(now);

        return toListItem(clientRepository.save(client));
    }

    @Transactional(readOnly = true)
    public List<ClientListItemResponse> listPortfolio(String status, String serviceStatus, BigDecimal minLtv, Integer tenureDays) {
        UUID tenantId = authContext.tenantId();
        Instant tenureCutoff = tenureDays != null
            ? Instant.now().minus(tenureDays, ChronoUnit.DAYS)
            : Instant.EPOCH;

        return clientRepository.findPortfolio(tenantId, status, serviceStatus, minLtv, tenureDays, tenureCutoff)
            .stream()
            .map(this::toListItem)
            .toList();
    }

    @Transactional(readOnly = true)
    public Client360Response getClient360(UUID clientId) {
        UUID tenantId = authContext.tenantId();
        Client client = clientRepository.findDetail(tenantId, clientId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cliente não encontrado"));

        List<ClientContactHistoryItem> contacts = List.of();
        if (client.getLead() != null) {
            contacts = outreachMessageRepository.findByLeadIdOrderByCreatedAtDesc(client.getLead().getId())
                .stream()
                .map(this::toContactItem)
                .toList();
        }

        List<ClientProposalHistoryItem> proposals = proposalRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream()
            .filter(p -> client.getLead() != null && client.getLead().getId().equals(p.getLead() != null ? p.getLead().getId() : null)
                || client.getId().equals(p.getClient() != null ? p.getClient().getId() : null))
            .map(this::toProposalItem)
            .toList();

        List<ClientServiceItem> services = projectRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
            .stream()
            .filter(p -> p.getClient().getId().equals(client.getId()))
            .map(this::toServiceItem)
            .toList();

        long tenureDays = ChronoUnit.DAYS.between(client.getContractedAt(), Instant.now());

        return new Client360Response(
            client.getId(),
            client.getLegalName(),
            client.getTradeName(),
            client.getDocument(),
            client.getEmail(),
            client.getPhone(),
            client.getCity(),
            client.getState(),
            client.getStatus(),
            client.getServiceStatus(),
            client.getLifetimeValue(),
            client.getContractedAt(),
            tenureDays,
            client.getOwner() != null ? client.getOwner().getFullName() : null,
            contacts,
            proposals,
            services
        );
    }

    private Company createManualCompany(CreateClientRequest request, String document, String legalName, String city, String state) {
        Instant now = Instant.now();
        Company company = new Company();
        company.setCnpj(document);
        company.setLegalName(legalName);
        company.setTradeName(trimToNull(request.tradeName()));
        company.setCnaeMain("0000000");
        company.setCnaeDescription("Cadastro manual");
        company.setCity(city);
        company.setState(state);
        company.setEmail(trimToNull(request.email()));
        company.setPhone(trimToNull(request.phone()));
        company.setDataSource("MANUAL");
        company.setGeocoded(false);
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        return companyRepository.save(company);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeCnpj(String document) {
        if (document == null) {
            return null;
        }
        String digits = document.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String normalizeState(String state) {
        String normalized = trimToNull(state);
        return normalized != null ? normalized.toUpperCase() : null;
    }

    private ClientListItemResponse toListItem(Client client) {
        long tenureDays = ChronoUnit.DAYS.between(client.getContractedAt(), Instant.now());
        return new ClientListItemResponse(
            client.getId(),
            client.getTradeName() != null ? client.getTradeName() : client.getLegalName(),
            client.getDocument(),
            client.getCity(),
            client.getState(),
            client.getStatus(),
            client.getServiceStatus(),
            client.getLifetimeValue(),
            tenureDays,
            client.getOwner() != null ? client.getOwner().getFullName() : null,
            client.getEmail(),
            client.getPhone()
        );
    }

    private ClientContactHistoryItem toContactItem(OutreachMessage message) {
        return new ClientContactHistoryItem(
            message.getId(),
            message.getChannel(),
            message.getSubject(),
            message.getStatus(),
            message.getSentAt(),
            message.getRepliedAt()
        );
    }

    private ClientProposalHistoryItem toProposalItem(Proposal proposal) {
        return new ClientProposalHistoryItem(
            proposal.getId(),
            proposal.getTitle(),
            proposal.getStatus(),
            proposal.getTotalAmount(),
            proposal.getCreatedAt(),
            proposal.getApprovedAt(),
            proposal.getRejectedAt()
        );
    }

    private ClientServiceItem toServiceItem(Project project) {
        return new ClientServiceItem(
            project.getId(),
            project.getName(),
            project.getStatus(),
            project.getProgressPercent(),
            project.getStartedAt(),
            project.getCompletedAt()
        );
    }
}
