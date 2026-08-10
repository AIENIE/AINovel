package com.ainovel.app.adminauth;

import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class SystemEnvAdminAuthPolicySource implements AdminAuthPolicySource {
    private final Function<String, String> environment;

    public SystemEnvAdminAuthPolicySource() {
        this(System::getenv);
    }

    SystemEnvAdminAuthPolicySource(Function<String, String> environment) {
        this.environment = environment;
    }

    @Override
    public String env() {
        return exact("ENV", environment.apply("ENV"), ENV_LOCAL, ENV_TEST, ENV_PRODUCTION);
    }

    @Override
    public String authMode() {
        return exact("AUTH_MODE", environment.apply("AUTH_MODE"), AUTH_MODE_PASSWORD, AUTH_MODE_TOTP);
    }

    private String exact(String name, String value, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        throw new IllegalStateException(name + " must be exactly one of " + String.join(", ", allowed)
                + "; got '" + value + "'");
    }
}
