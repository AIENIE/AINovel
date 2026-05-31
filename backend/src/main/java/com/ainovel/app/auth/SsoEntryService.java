package com.ainovel.app.auth;

import com.ainovel.app.integration.ConsulServiceResolver;
import com.ainovel.app.integration.ExternalServiceProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class SsoEntryService {

    private final ExternalServiceProperties properties;

    public SsoEntryService(ExternalServiceProperties properties) {
        this.properties = properties;
    }

    public URI buildLoginRedirectUri(String callbackUrl, String state) {
        return buildRedirectUri("/sso/login", callbackUrl, state);
    }

    public URI buildRegisterRedirectUri(String callbackUrl, String state) {
        return buildRedirectUri("/register", callbackUrl, state);
    }

    private URI buildRedirectUri(String path, String callbackUrl, String state) {
        String baseUrl = resolveUserServiceBaseUrl();
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .queryParam("redirect", callbackUrl)
                .queryParam("state", state)
                .build()
                .toUri();
    }

    private String resolveUserServiceBaseUrl() {
        ExternalServiceProperties.ServiceTarget target = properties.getUserserviceHttp();
        String preferred = normalizeHttpUrl(target.getAddress());
        if (preferred != null) {
            return preferred;
        }
        return normalizeBaseUrl(target.getAddress());
    }

    private String normalizeHttpUrl(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String value = address.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            if (value.endsWith("/")) {
                return value.substring(0, value.length() - 1);
            }
            return value;
        }
        return null;
    }

    private String normalizeBaseUrl(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalStateException("userservice-http address is empty");
        }
        String value = address.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            var endpoint = ConsulServiceResolver.parseAddress(value);
            if (endpoint.isEmpty()) {
                throw new IllegalStateException("Invalid userservice-http address: " + address);
            }
            return endpoint.get().toHttpBase();
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
