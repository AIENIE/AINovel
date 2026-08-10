package com.ainovel.app.adminauth;

public final class AdminAuthConstants {
    public static final String ADMIN_SESSION_COOKIE = "AINOVEL_ADMIN_SESSION";
    public static final String FULL_AUTHORITY = "AUTH_LOCAL_ADMIN";
    public static final String RECOVERY_AUTHORITY = "AUTH_LOCAL_ADMIN_RECOVERY";
    public static final String OPERATION_PROOF_HEADER = "X-Admin-Operation-Proof";

    private AdminAuthConstants() {
    }
}
