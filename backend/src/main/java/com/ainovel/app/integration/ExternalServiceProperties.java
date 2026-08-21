package com.ainovel.app.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.external")
public class ExternalServiceProperties {

    private String projectKey = "ainovel";
    private long timeoutMs = 2500;
    private long aiTimeoutMs = 60_000;
    private long billingTimeoutMs = 5_000;
    private long sessionTimeoutMs = 3_000;
    private final Grpc grpc = new Grpc();
    private final Security security = new Security();
    private final ServiceTarget userserviceHttp = new ServiceTarget();
    private final ServiceTarget aiserviceGrpc = new ServiceTarget();
    private final ServiceTarget payserviceGrpc = new ServiceTarget();

    public ExternalServiceProperties() {
        userserviceHttp.setAddress("https://userservice.seekerhut.com");
        aiserviceGrpc.setAddress("static://aiservice.seekerhut.com:12011");
        payserviceGrpc.setAddress("static://payservice.seekerhut.com:12021");
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getAiTimeoutMs() { return aiTimeoutMs; }
    public void setAiTimeoutMs(long aiTimeoutMs) { this.aiTimeoutMs = aiTimeoutMs; }
    public long getBillingTimeoutMs() { return billingTimeoutMs; }
    public void setBillingTimeoutMs(long billingTimeoutMs) { this.billingTimeoutMs = billingTimeoutMs; }
    public long getSessionTimeoutMs() { return sessionTimeoutMs; }
    public void setSessionTimeoutMs(long sessionTimeoutMs) { this.sessionTimeoutMs = sessionTimeoutMs; }

    public Grpc getGrpc() {
        return grpc;
    }

    public Security getSecurity() {
        return security;
    }

    public ServiceTarget getUserserviceHttp() {
        return userserviceHttp;
    }

    public ServiceTarget getAiserviceGrpc() {
        return aiserviceGrpc;
    }

    public ServiceTarget getPayserviceGrpc() {
        return payserviceGrpc;
    }

    public static class ServiceTarget {
        private String address;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    public static class Grpc {
        private boolean tlsEnabled = true;
        private boolean plaintextEnabled = false;
        private String trustCertCollection = "";

        public boolean isTlsEnabled() {
            return tlsEnabled;
        }

        public void setTlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
        }

        public boolean isPlaintextEnabled() {
            return plaintextEnabled;
        }

        public void setPlaintextEnabled(boolean plaintextEnabled) {
            this.plaintextEnabled = plaintextEnabled;
        }

        public String getTrustCertCollection() {
            return trustCertCollection;
        }

        public void setTrustCertCollection(String trustCertCollection) {
            this.trustCertCollection = trustCertCollection;
        }
    }

    public static class Security {
        private boolean failFast = true;
        private final Ai ai = new Ai();
        private final User user = new User();
        private final Pay pay = new Pay();

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }

        public Ai getAi() {
            return ai;
        }

        public User getUser() {
            return user;
        }

        public Pay getPay() {
            return pay;
        }
    }

    public static class Ai {
        private String hmacCaller = "";
        private String hmacSecret = "";

        public String getHmacCaller() {
            return hmacCaller;
        }

        public void setHmacCaller(String hmacCaller) {
            this.hmacCaller = hmacCaller;
        }

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }
    }

    public static class User {
        private String callerId = "ainovel";
        private String issuer = "ainovel";
        private String secret = "";
        private String audience = UserServiceJwtConfigurationValidator.REQUIRED_AUDIENCE;
        private long ttlSeconds = 300L;
        private String scopes = UserServiceJwtConfigurationValidator.REQUIRED_SCOPE;

        public String getCallerId() {
            return callerId;
        }

        public void setCallerId(String callerId) {
            this.callerId = callerId;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }
    }

    public static class Pay {
        private String callerId = PayServiceJwtConfigurationValidator.REQUIRED_CALLER_ID;
        private String issuer = PayServiceJwtConfigurationValidator.REQUIRED_CALLER_ID;
        private String serviceName = PayServiceJwtConfigurationValidator.REQUIRED_CALLER_ID;
        private String secret = "";
        private String audience = PayServiceJwtConfigurationValidator.REQUIRED_AUDIENCE;
        private String role = PayServiceJwtConfigurationValidator.REQUIRED_ROLE;
        private long ttlSeconds = 300L;
        private String scopes = String.join(",", PayServiceJwtConfigurationValidator.REQUIRED_SCOPES);
        /** Detection-only compatibility input. It is never attached to an RPC. */
        private String legacyStaticToken = "";

        public String getCallerId() { return callerId; }
        public void setCallerId(String callerId) { this.callerId = callerId; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }
        public String getLegacyStaticToken() { return legacyStaticToken; }
        public void setLegacyStaticToken(String legacyStaticToken) { this.legacyStaticToken = legacyStaticToken; }
    }
}
