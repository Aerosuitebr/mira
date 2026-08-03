package com.prospectportal.module.client;

import com.prospectportal.module.client.entity.Client;
import com.prospectportal.module.client.repository.ClientRepository;
import com.prospectportal.module.crm.entity.CrmCard;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.common.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ClientConversionService {

    private final ClientRepository clientRepository;

    public ClientConversionService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional
    public Client ensureFromWonDeal(Lead lead, BigDecimal dealValue, User fallbackOwner) {
        UUID tenantId = lead.getTenant().getId();
        boolean incrementLtv = !"WON".equals(lead.getStatus());
        return clientRepository.findByTenantIdAndCompanyId(tenantId, lead.getCompany().getId())
            .map(existing -> refreshClient(existing, lead, dealValue, incrementLtv))
            .orElseGet(() -> createClient(lead, dealValue, fallbackOwner));
    }

    @Transactional
    public Client ensureFromCard(CrmCard card) {
        Lead lead = card.getLead();
        BigDecimal value = card.getValueAmount() != null ? card.getValueAmount() : BigDecimal.ZERO;
        return ensureFromWonDeal(lead, value, lead.getOwner());
    }

    private Client refreshClient(Client client, Lead lead, BigDecimal dealValue, boolean incrementLtv) {
        Company company = lead.getCompany();
        client.setStatus("ACTIVE");
        client.setServiceStatus("ACTIVE");
        if (incrementLtv && dealValue != null && dealValue.compareTo(BigDecimal.ZERO) > 0) {
            client.setLifetimeValue(client.getLifetimeValue().add(dealValue));
        }
        client.setTradeName(company.getTradeName());
        client.setLegalName(company.getLegalName());
        client.setDocument(company.getCnpj());
        client.setEmail(company.getEmail());
        client.setPhone(company.getPhone());
        client.setCity(company.getCity());
        client.setState(company.getState());
        client.setUpdatedAt(Instant.now());
        return clientRepository.save(client);
    }

    private Client createClient(Lead lead, BigDecimal dealValue, User owner) {
        Company company = lead.getCompany();
        Client client = new Client();
        client.setTenant(lead.getTenant());
        client.setLead(lead);
        client.setCompany(company);
        client.setOwner(owner != null ? owner : lead.getOwner());
        client.setLegalName(company.getLegalName());
        client.setTradeName(company.getTradeName());
        client.setDocument(company.getCnpj());
        client.setEmail(company.getEmail());
        client.setPhone(company.getPhone());
        client.setCity(company.getCity());
        client.setState(company.getState());
        client.setStatus("ACTIVE");
        client.setServiceStatus("ACTIVE");
        client.setLifetimeValue(dealValue != null ? dealValue : BigDecimal.ZERO);
        Instant now = Instant.now();
        client.setContractedAt(now);
        client.setCreatedAt(now);
        client.setUpdatedAt(now);
        return clientRepository.save(client);
    }
}
