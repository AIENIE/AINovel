package com.ainovel.app.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.external")
public class ExternalServiceProperties {

    private String projectKey = "ainovel";
    private long timeoutMs = 2500;
    private final Grpc grpc = new Grpc();
    private final Security security = new Security();
    private final ServiceTarget userserviceHttp = new ServiceTarget();
    private final ServiceTarget aiserviceGrpc = new ServiceTarget();
    private final ServiceTarget payserviceGrpc = new ServiceTarget();

    public ExternalServiceProperties() {
        userserviceHttp.setAddress("https://userservice.localhut.com");
        aiserviceGrpc.setAddress("static://aiservice.localhut.com:10011");
        payserviceGrpc.setAddress("static://payservice.localhut.com:10021");
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
        private boolean plaintextEnabled = true;

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
        private String internalGrpcToken = "";

        public String getInternalGrpcToken() {
            return internalGrpcToken;
        }

        public void setInternalGrpcToken(String internalGrpcToken) {
            this.internalGrpcToken = internalGrpcToken;
        }
    }

    public static class Pay {
        private String serviceJwt = "";
        private String jwtIssuer = "aienie-services";
        private String jwtAudience = "aienie-payservice-grpc";
        private String requiredRole = "SERVICE";
        private String requiredScopes = "billing.balance.read,billing.balance.convert,billing.grant.write,billing.usage.deduct,billing.checkin.read,billing.checkin.write,billing.redeem.write,billing.ledger.read";

        public String getServiceJwt() {
            return serviceJwt;
        }

        public void setServiceJwt(String serviceJwt) {
            this.serviceJwt = serviceJwt;
        }

        public String getJwtIssuer() {
            return jwtIssuer;
        }

        public void setJwtIssuer(String jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
        }

        public String getJwtAudience() {
            return jwtAudience;
        }

        public void setJwtAudience(String jwtAudience) {
            this.jwtAudience = jwtAudience;
        }

        public String getRequiredRole() {
            return requiredRole;
        }

        public void setRequiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
        }

        public String getRequiredScopes() {
            return requiredScopes;
        }

        public void setRequiredScopes(String requiredScopes) {
            this.requiredScopes = requiredScopes;
        }
    }
}
