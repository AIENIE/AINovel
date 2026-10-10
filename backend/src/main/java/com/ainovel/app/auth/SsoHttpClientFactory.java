package com.ainovel.app.auth;

import com.ainovel.app.integration.ExternalServiceProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;

/** Uses the mounted environment CA for staging HTTPS, with normal hostname verification. */
@Component
public final class SsoHttpClientFactory {
    private static final String STAGING_ROOT = "/run/aienie/trust/staging-root.pem";
    private final String environment;
    private final ExternalServiceProperties properties;

    public SsoHttpClientFactory(@Value("${ENV:}") String environment, ExternalServiceProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    public HttpClient create() {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
        String trust = properties.getGrpc().getTrustCertCollection();
        if ("test".equals(environment)) {
            if (!STAGING_ROOT.equals(trust)) {
                throw new IllegalStateException("staging SSO HTTPS requires the target-policy trust bundle");
            }
            builder.sslContext(loadTrust(Path.of(trust)));
        } else if ("production".equals(environment) && trust != null && !trust.isBlank()) {
            throw new IllegalStateException("production SSO HTTPS must use system trust");
        }
        return builder.build();
    }

    static SSLContext loadTrust(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalStateException("SSO trust certificate must be a regular non-link file");
        }
        try (var input = Files.newInputStream(path)) {
            var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
            if (certificates.isEmpty()) throw new IllegalStateException("SSO trust bundle is empty");
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (var certificate : certificates) store.setCertificateEntry("environment-root-" + index++, certificate);
            TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, managers.getTrustManagers(), null);
            return context;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to load SSO environment trust bundle", failure);
        }
    }
}
