package com.ainovel.app.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
    Optional<ExternalIdentity> findByIssuerAndRemoteUid(String issuer, long remoteUid);
    Optional<ExternalIdentity> findByUserAndIssuer(User user, String issuer);
}
