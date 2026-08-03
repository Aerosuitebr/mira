package com.prospectportal.module.proposal;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.common.repository.UserRepository;
import com.prospectportal.module.client.entity.Client;
import com.prospectportal.module.client.repository.ClientRepository;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.crm.repository.LeadRepository;
import com.prospectportal.module.discovery.FlowTestFixtureService;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.proposal.entity.Proposal;
import com.prospectportal.module.proposal.entity.ProposalItem;
import com.prospectportal.module.proposal.event.ProposalApprovedEvent;
import com.prospectportal.module.proposal.repository.ProposalItemRepository;
import com.prospectportal.module.proposal.repository.ProposalRepository;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.ApproveProposalRequest;
import com.prospectportal.web.dto.CreateProposalRequest;
import com.prospectportal.web.dto.ProposalItemRequest;
import com.prospectportal.web.dto.ProposalItemResponse;
import com.prospectportal.web.dto.ProposalRecipientOption;
import com.prospectportal.web.dto.ProposalResponse;
import com.prospectportal.web.dto.PublicProposalResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProposalService {

    private final AuthContext authContext;
    private final ProposalRepository proposalRepository;
    private final ProposalItemRepository proposalItemRepository;
    private final LeadRepository leadRepository;
    private final ClientRepository clientRepository;
    private final CompanyRepository companyRepository;
    private final FlowTestFixtureService flowTestFixtureService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final String publicBaseUrl;

    public ProposalService(
        AuthContext authContext,
        ProposalRepository proposalRepository,
        ProposalItemRepository proposalItemRepository,
        LeadRepository leadRepository,
        ClientRepository clientRepository,
        CompanyRepository companyRepository,
        FlowTestFixtureService flowTestFixtureService,
        TenantRepository tenantRepository,
        UserRepository userRepository,
        ApplicationEventPublisher eventPublisher,
        @Value("${app.public-base-url:http://localhost:4201}") String publicBaseUrl
    ) {
        this.authContext = authContext;
        this.proposalRepository = proposalRepository;
        this.proposalItemRepository = proposalItemRepository;
        this.leadRepository = leadRepository;
        this.clientRepository = clientRepository;
        this.companyRepository = companyRepository;
        this.flowTestFixtureService = flowTestFixtureService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public List<ProposalRecipientOption> listRecipients(String search) {
        UUID tenantId = authContext.tenantId();
        String term = search != null ? search.trim().toLowerCase() : "";

        if (flowTestFixtureService.isEnabled()) {
            return flowTestFixtureService.loadResponses().stream()
                .map(company -> new ProposalRecipientOption(
                    ProposalRecipientOption.companyKey(company.id()),
                    company.tradeName() != null && !company.tradeName().isBlank()
                        ? company.tradeName()
                        : company.legalName(),
                    "Fixture · " + company.city() + "/" + company.state() + " · " + (company.email() != null ? company.email() : ""),
                    company.phone(),
                    "Descoberta"
                ))
                .filter(option -> term.isEmpty()
                    || option.label().toLowerCase().contains(term)
                    || option.subtitle().toLowerCase().contains(term)
                    || (option.phone() != null && option.phone().contains(term)))
                .toList();
        }

        List<Lead> tenantLeads = leadRepository.findByTenantWithCompany(tenantId);
        List<ProposalRecipientOption> leads = tenantLeads.stream()
            .map(lead -> {
                var company = lead.getCompany();
                String label = company.getTradeName() != null ? company.getTradeName() : company.getLegalName();
                return new ProposalRecipientOption(
                    ProposalRecipientOption.leadKey(lead.getId()),
                    label,
                    "Lead · " + company.getCity() + "/" + company.getState(),
                    company.getPhone(),
                    "CRM"
                );
            })
            .toList();

        var portfolio = clientRepository.findPortfolio(tenantId, null, null, null, null, Instant.EPOCH);
        List<ProposalRecipientOption> clients = portfolio.stream()
            .map(client -> new ProposalRecipientOption(
                ProposalRecipientOption.clientKey(client.getId()),
                client.getTradeName() != null ? client.getTradeName() : client.getLegalName(),
                "Carteira · " + client.getCity() + "/" + client.getState(),
                client.getPhone(),
                "Carteira"
            ))
            .toList();

        List<ProposalRecipientOption> linked = new ArrayList<>();
        linked.addAll(leads);
        linked.addAll(clients);

        List<ProposalRecipientOption> filteredLinked = linked.stream()
            .filter(option -> term.isEmpty()
                || option.label().toLowerCase().contains(term)
                || option.subtitle().toLowerCase().contains(term))
            .toList();

        if (term.length() < 2) {
            return filteredLinked;
        }

        Set<String> labelsAlreadyListed = new HashSet<>();
        for (ProposalRecipientOption option : filteredLinked) {
            labelsAlreadyListed.add(option.label().toLowerCase());
        }

        Set<UUID> linkedCompanyIds = new HashSet<>();
        tenantLeads.forEach(lead -> {
            if (lead.getCompany() != null) {
                linkedCompanyIds.add(lead.getCompany().getId());
            }
        });
        portfolio.forEach(client -> {
            if (client.getCompany() != null) {
                linkedCompanyIds.add(client.getCompany().getId());
            }
        });

        String digits = term.replaceAll("\\D", "");
        List<ProposalRecipientOption> discovered = companyRepository
            .searchForRecipients(term, digits.isEmpty() ? term : digits, 20)
            .stream()
            .filter(company -> !linkedCompanyIds.contains(company.getId()))
            .map(company -> {
                String label = company.getTradeName() != null ? company.getTradeName() : company.getLegalName();
                return new ProposalRecipientOption(
                    ProposalRecipientOption.companyKey(company.getId()),
                    label,
                    "Descoberta · " + company.getCity() + "/" + company.getState(),
                    company.getPhone(),
                    "Descoberta"
                );
            })
            .filter(option -> !labelsAlreadyListed.contains(option.label().toLowerCase()))
            .toList();

        List<ProposalRecipientOption> result = new ArrayList<>(filteredLinked);
        result.addAll(discovered);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> list() {
        return proposalRepository.findByTenantIdOrderByCreatedAtDesc(authContext.tenantId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProposalResponse get(UUID id) {
        Proposal proposal = proposalRepository.findByTenantIdAndId(authContext.tenantId(), id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Proposta não encontrada"));
        return toResponse(proposal);
    }

    @Transactional
    public ProposalResponse create(CreateProposalRequest request) {
        validateItems(request.items());

        UUID tenantId = authContext.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant não encontrado"));

        int destinations = 0;
        if (request.leadId() != null) {
            destinations++;
        }
        if (request.clientId() != null) {
            destinations++;
        }
        if (request.companyId() != null) {
            destinations++;
        }
        if (destinations > 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Informe apenas um destinatário: lead, cliente ou empresa");
        }

        Lead lead = null;
        Client client = null;
        if (request.leadId() != null) {
            lead = leadRepository.findById(request.leadId())
                .filter(l -> l.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead não encontrado"));
        }
        if (request.clientId() != null) {
            client = clientRepository.findDetail(tenantId, request.clientId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Cliente não encontrado"));
        }
        if (request.companyId() != null) {
            lead = resolveOrCreateLead(tenant, request.companyId());
        }

        Instant now = Instant.now();
        Proposal proposal = new Proposal();
        proposal.setTenant(tenant);
        proposal.setLead(lead);
        proposal.setClient(client);
        proposal.setCreatedBy(userRepository.findById(authContext.userId()).orElse(null));
        proposal.setTitle(request.title());
        proposal.setPaymentTerms(request.paymentTerms());
        proposal.setValidityDays(request.validityDays() != null ? request.validityDays() : 15);
        proposal.setStatus("DRAFT");
        proposal.setApprovalToken(UUID.randomUUID());
        proposal.setTotalAmount(BigDecimal.ZERO);
        proposal.setCreatedAt(now);
        proposal.setUpdatedAt(now);
        proposal = proposalRepository.save(proposal);

        saveItems(proposal, request.items());
        proposal.setTotalAmount(calculateTotal(proposal.getId()));
        proposal.setUpdatedAt(Instant.now());
        proposal = proposalRepository.save(proposal);
        return toResponse(proposal);
    }

    @Transactional
    public ProposalResponse publish(UUID id) {
        Proposal proposal = proposalRepository.findByTenantIdAndId(authContext.tenantId(), id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Proposta não encontrada"));
        if (!"DRAFT".equals(proposal.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Somente rascunhos podem ser publicados");
        }
        if (proposal.getLead() == null && proposal.getClient() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Selecione o cliente ou lead de destino antes de publicar");
        }
        if (calculateTotal(proposal.getId()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "A proposta precisa ter valor total maior que zero");
        }

        Instant now = Instant.now();
        proposal.setStatus("PENDING");
        proposal.setExpiresAt(now.plus(proposal.getValidityDays(), ChronoUnit.DAYS));
        proposal.setUpdatedAt(now);
        return toResponse(proposalRepository.save(proposal));
    }

    @Transactional(readOnly = true)
    public PublicProposalResponse getPublic(UUID token) {
        Proposal proposal = proposalRepository.findByApprovalToken(token)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Proposta não encontrada"));

        if (isExpired(proposal)) {
            if ("PENDING".equals(proposal.getStatus())) {
                proposal.setStatus("EXPIRED");
                proposal.setUpdatedAt(Instant.now());
                proposalRepository.save(proposal);
            }
            throw new ResponseStatusException(BAD_REQUEST, "Proposta expirada");
        }

        String companyName = resolveCompanyName(proposal);

        return new PublicProposalResponse(
            companyName,
            proposal.getTitle(),
            proposal.getTotalAmount(),
            proposal.getPaymentTerms(),
            resolveExpiresAt(proposal),
            proposal.getStatus(),
            loadItemResponses(proposal.getId())
        );
    }

    @Transactional
    public void approvePublic(UUID token, ApproveProposalRequest request) {
        Proposal proposal = proposalRepository.findByApprovalToken(token)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Proposta não encontrada"));

        if (!"PENDING".equals(proposal.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Proposta não está pendente de aprovação");
        }
        if (isExpired(proposal)) {
            proposal.setStatus("EXPIRED");
            proposal.setUpdatedAt(Instant.now());
            proposalRepository.save(proposal);
            throw new ResponseStatusException(BAD_REQUEST, "Proposta expirada");
        }

        Instant now = Instant.now();
        proposal.setStatus("APPROVED");
        proposal.setApprovedAt(now);
        proposal.setSignerName(request.signerName());
        proposal.setSignerDocument(request.signerDocument());
        proposal.setUpdatedAt(now);
        proposalRepository.save(proposal);

        eventPublisher.publishEvent(new ProposalApprovedEvent(
            proposal.getTenant().getId(),
            proposal.getId(),
            proposal.getLead() != null ? proposal.getLead().getId() : null,
            proposal.getTotalAmount(),
            request.signerName(),
            request.signerDocument()
        ));
    }

    private void saveItems(Proposal proposal, List<ProposalItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int order = 0;
        for (ProposalItemRequest item : items) {
            ProposalItem entity = new ProposalItem();
            entity.setProposal(proposal);
            entity.setDescription(item.description());
            entity.setQuantity(item.quantity() != null ? item.quantity() : BigDecimal.ONE);
            entity.setUnitPrice(item.unitPrice() != null ? item.unitPrice() : BigDecimal.ZERO);
            entity.setSortOrder(order++);
            proposalItemRepository.save(entity);
        }
    }

    private BigDecimal calculateTotal(UUID proposalId) {
        return proposalItemRepository.findByProposalIdOrderBySortOrderAsc(proposalId).stream()
            .map(item -> item.getUnitPrice().multiply(item.getQuantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ProposalItemResponse> loadItemResponses(UUID proposalId) {
        return proposalItemRepository.findByProposalIdOrderBySortOrderAsc(proposalId).stream()
            .map(item -> new ProposalItemResponse(
                item.getId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSortOrder()
            ))
            .toList();
    }

    private void validateItems(List<ProposalItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Adicione ao menos um item à proposta");
        }
        for (ProposalItemRequest item : items) {
            if (item.description() == null || item.description().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Descrição do item é obrigatória");
            }
            BigDecimal quantity = item.quantity() != null ? item.quantity() : BigDecimal.ZERO;
            BigDecimal unitPrice = item.unitPrice() != null ? item.unitPrice() : BigDecimal.ZERO;
            if (quantity.compareTo(BigDecimal.ZERO) <= 0 || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Quantidade e valor unitário devem ser maiores que zero");
            }
        }
    }

    private Lead resolveOrCreateLead(Tenant tenant, UUID companyId) {
        return leadRepository.findByTenantIdAndCompanyId(tenant.getId(), companyId)
            .orElseGet(() -> {
                Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Empresa não encontrada"));
                Lead lead = new Lead();
                lead.setTenant(tenant);
                lead.setCompany(company);
                lead.setStatus("NEW");
                lead.setScore(45);
                Instant now = Instant.now();
                lead.setCreatedAt(now);
                lead.setUpdatedAt(now);
                return leadRepository.save(lead);
            });
    }

    private String resolveCompanyName(Proposal proposal) {
        if (proposal.getLead() != null) {
            var company = proposal.getLead().getCompany();
            return company.getTradeName() != null ? company.getTradeName() : company.getLegalName();
        }
        if (proposal.getClient() != null) {
            Client client = proposal.getClient();
            return client.getTradeName() != null ? client.getTradeName() : client.getLegalName();
        }
        return "Cliente";
    }

    private Instant resolveExpiresAt(Proposal proposal) {
        if (proposal.getExpiresAt() != null) {
            return proposal.getExpiresAt();
        }
        return proposal.getCreatedAt().plus(proposal.getValidityDays(), ChronoUnit.DAYS);
    }

    private boolean isExpired(Proposal proposal) {
        return Instant.now().isAfter(resolveExpiresAt(proposal));
    }

    private ProposalResponse toResponse(Proposal proposal) {
        return new ProposalResponse(
            proposal.getId(),
            proposal.getLead() != null ? proposal.getLead().getId() : null,
            proposal.getTitle(),
            proposal.getTotalAmount(),
            proposal.getStatus(),
            proposal.getPaymentTerms(),
            proposal.getValidityDays(),
            proposal.getApprovalToken(),
            publicBaseUrl + "/proposta/" + proposal.getApprovalToken(),
            proposal.getCreatedAt(),
            loadItemResponses(proposal.getId())
        );
    }
}
