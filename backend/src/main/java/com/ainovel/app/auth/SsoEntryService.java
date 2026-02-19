package com.ainovel.app.auth;

import com.ainovel.app.integration.ConsulServiceResolver;
import com.ainovel.app.integration.ExternalServiceProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@Service
public class SsoEntryService {

    private final ExternalServiceProperties properties;
    private final ConsulServiceResolver resolver;

    public SsoEntryService(ExternalServiceProperties properties, ConsulServiceResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
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
        Optional<ConsulServiceResolver.Endpoint> endpoint =
                resolver.resolveOrFallback(target.getServiceName(), target.getFallback());
        if (endpoint.isPresent()) {
            return endpoint.get().toHttpBase();
        }
        return normalizeBaseUrl(target.getFallback());
    }

    private String normalizeBaseUrl(String fallback) {
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("userservice-http fallback is empty");
        }
        String value = fallback.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            Optional<ConsulServiceResolver.Endpoint> endpoint = ConsulServiceResolver.parseAddress(value);
            if (endpoint.isEmpty()) {
                throw new IllegalStateException("Invalid userservice-http fallback: " + fallback);
            }
            return endpoint.get().toHttpBase();
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
