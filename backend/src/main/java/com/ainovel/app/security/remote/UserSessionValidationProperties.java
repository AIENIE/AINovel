package com.ainovel.app.security.remote;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sso.session-validation")
public class UserSessionValidationProperties {

    private boolean enabled = true;
    private long timeoutMs = 2000;
    private String grpcAddress = "static://userservice.localhut.com:10001";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getGrpcAddress() {
        return grpcAddress;
    }

    public void setGrpcAddress(String grpcAddress) {
        this.grpcAddress = grpcAddress;
    }
}
