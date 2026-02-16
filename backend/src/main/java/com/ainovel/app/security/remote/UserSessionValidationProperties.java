package com.ainovel.app.security.remote;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sso.session-validation")
public class UserSessionValidationProperties {

    private boolean enabled = true;
    private long timeoutMs = 2000;
    private String grpcFallbackAddress = "static://127.0.0.1:13001";
    private final Consul consul = new Consul();

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

    public String getGrpcFallbackAddress() {
        return grpcFallbackAddress;
    }

    public void setGrpcFallbackAddress(String grpcFallbackAddress) {
        this.grpcFallbackAddress = grpcFallbackAddress;
    }

    public Consul getConsul() {
        return consul;
    }

    public static class Consul {
        private boolean enabled = true;
        private String scheme = "http";
        private String host = "192.168.1.4";
        private int port = 60000;
        private String serviceName = "aienie-userservice-grpc";
        private String datacenter = "";
        private String tag = "";
        private long cacheSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getDatacenter() {
            return datacenter;
        }

        public void setDatacenter(String datacenter) {
            this.datacenter = datacenter;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public long getCacheSeconds() {
            return cacheSeconds;
        }

        public void setCacheSeconds(long cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }
    }
}
