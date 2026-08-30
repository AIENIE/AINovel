package com.ainovel.app.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Keeps the private staging root out of production while pinning it in staging. */
@Component
@Profile("!test")
public final class StagingTrustPolicy {
    static final String STAGING_ROOT = "/run/aienie/trust/staging-root.pem";

    private final String runtimeEnvironment;
    private final String trustCertCollection;
    private final boolean tlsEnabled;
    private final boolean plaintextEnabled;

    public StagingTrustPolicy(
            @Value("${ENV:}") String runtimeEnvironment,
            @Value("${app.external.grpc.trust-cert-collection:}") String trustCertCollection,
            @Value("${app.external.grpc.tls-enabled:true}") boolean tlsEnabled,
            @Value("${app.external.grpc.plaintext-enabled:false}") boolean plaintextEnabled) {
        this.runtimeEnvironment = runtimeEnvironment;
        this.trustCertCollection = trustCertCollection;
        this.tlsEnabled = tlsEnabled;
        this.plaintextEnabled = plaintextEnabled;
    }

    @PostConstruct
    void validate() {
        if ("test".equals(runtimeEnvironment)
                && (!tlsEnabled || plaintextEnabled || !STAGING_ROOT.equals(trustCertCollection))) {
            throw new IllegalStateException("staging gRPC must use the target-policy trust bundle");
        }
        if ("production".equals(runtimeEnvironment) && !trustCertCollection.isBlank()) {
            throw new IllegalStateException("production must reject the staging trust bundle");
        }
    }
}
