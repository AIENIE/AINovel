package com.ainovel.app.adminauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.admin-auth")
public class AdminLocalAuthProperties {
    private String username = "admin";
    private String password = "";
    private String encryptionKeys = "";
    private String activeKeyVersion = "v1";
    private int sessionMinutes = 120;
    private int sessionIdleMinutes = 30;
    private int recoverySessionMinutes = 15;
    private int recoverySessionIdleMinutes = 10;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEncryptionKeys() { return encryptionKeys; }
    public void setEncryptionKeys(String encryptionKeys) { this.encryptionKeys = encryptionKeys; }
    public String getActiveKeyVersion() { return activeKeyVersion; }
    public void setActiveKeyVersion(String activeKeyVersion) { this.activeKeyVersion = activeKeyVersion; }
    public int getSessionMinutes() { return sessionMinutes; }
    public void setSessionMinutes(int sessionMinutes) { this.sessionMinutes = sessionMinutes; }
    public int getSessionIdleMinutes() { return sessionIdleMinutes; }
    public void setSessionIdleMinutes(int sessionIdleMinutes) { this.sessionIdleMinutes = sessionIdleMinutes; }
    public int getRecoverySessionMinutes() { return recoverySessionMinutes; }
    public void setRecoverySessionMinutes(int recoverySessionMinutes) { this.recoverySessionMinutes = recoverySessionMinutes; }
    public int getRecoverySessionIdleMinutes() { return recoverySessionIdleMinutes; }
    public void setRecoverySessionIdleMinutes(int recoverySessionIdleMinutes) { this.recoverySessionIdleMinutes = recoverySessionIdleMinutes; }
}
