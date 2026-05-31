package com.ainovel.app.integration;

import java.net.URI;
import java.util.Optional;

public final class ConsulServiceResolver {

    private ConsulServiceResolver() {
    }

    public static Optional<Endpoint> parseAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() != null && uri.getPort() > 0) {
                    return Optional.of(new Endpoint(uri.getHost(), uri.getPort()));
                }
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        if (value.startsWith("static://")) {
            value = value.substring("static://".length());
        }
        if (value.contains("://")) {
            try {
                URI uri = URI.create(value);
                if (uri.getHost() != null && uri.getPort() > 0) {
                    return Optional.of(new Endpoint(uri.getHost(), uri.getPort()));
                }
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }

        int idx = value.lastIndexOf(':');
        if (idx <= 0 || idx >= value.length() - 1) {
            return Optional.empty();
        }
        String host = value.substring(0, idx).trim();
        String portStr = value.substring(idx + 1).trim();
        if (host.isBlank()) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(portStr);
            if (port <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Endpoint(host, port));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public record Endpoint(String host, int port) {
        public String toHttpBase() {
            return "http://" + host + ":" + port;
        }

        public String toAuthority() {
            return host + ":" + port;
        }
    }

}
