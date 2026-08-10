package com.ainovel.app.adminauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.admin-auth")
public class AdminLocalAuthProperties {
    private String username = "";
    private String passwordHash = "";
    private String encryptionKeys = "";
    private String activeKeyVersion = "";
    private String trustedOrigins = "https://localainovel.testhut.top";
    private int sessionMinutes = 30;
    private int sessionIdleMinutes = 10;
    private int recoverySessionMinutes = 15;
    private int recoverySessionIdleMinutes = 10;
    private boolean cookieSecure = true;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEncryptionKeys() { return encryptionKeys; }
    public void setEncryptionKeys(String encryptionKeys) { this.encryptionKeys = encryptionKeys; }
    public String getActiveKeyVersion() { return activeKeyVersion; }
    public void setActiveKeyVersion(String activeKeyVersion) { this.activeKeyVersion = activeKeyVersion; }
    public String getTrustedOrigins() { return trustedOrigins; }
    public void setTrustedOrigins(String trustedOrigins) { this.trustedOrigins = trustedOrigins == null ? "" : trustedOrigins; }
    public int getSessionMinutes() { return sessionMinutes; }
    public void setSessionMinutes(int sessionMinutes) { this.sessionMinutes = sessionMinutes; }
    public int getSessionIdleMinutes() { return sessionIdleMinutes; }
    public void setSessionIdleMinutes(int sessionIdleMinutes) { this.sessionIdleMinutes = sessionIdleMinutes; }
    public int getRecoverySessionMinutes() { return recoverySessionMinutes; }
    public void setRecoverySessionMinutes(int recoverySessionMinutes) { this.recoverySessionMinutes = recoverySessionMinutes; }
    public int getRecoverySessionIdleMinutes() { return recoverySessionIdleMinutes; }
    public void setRecoverySessionIdleMinutes(int recoverySessionIdleMinutes) { this.recoverySessionIdleMinutes = recoverySessionIdleMinutes; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
}
