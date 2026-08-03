package com.prospectportal.module.portal.repository;

import com.prospectportal.module.portal.entity.PortalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PortalAccessTokenRepository extends JpaRepository<PortalAccessToken, UUID> {

    Optional<PortalAccessToken> findByToken(UUID token);
}
