package com.ainovel.app.auth;

import com.ainovel.app.integration.ExternalServiceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SsoHttpClientFactoryTest {
    @TempDir Path directory;

    @Test
    void localAndProductionRetainSystemTrust() throws Exception {
        var properties = new ExternalServiceProperties();
        assertSame(SSLContext.getDefault(), new SsoHttpClientFactory("production", properties).create().sslContext());
        assertSame(SSLContext.getDefault(), new SsoHttpClientFactory("develop", properties).create().sslContext());
    }

    @Test
    void productionRejectsPrivateRootAndStagingRejectsArbitraryPaths() {
        var properties = new ExternalServiceProperties();
        properties.getGrpc().setTrustCertCollection("/tmp/unapproved.pem");
        assertThrows(IllegalStateException.class, () -> new SsoHttpClientFactory("production", properties).create());
        assertThrows(IllegalStateException.class, () -> new SsoHttpClientFactory("test", properties).create());
        properties.getGrpc().setTrustCertCollection("");
        assertThrows(IllegalStateException.class, () -> new SsoHttpClientFactory("test", properties).create());
    }

    @Test
    void loadsCertificateCollectionWithoutDisablingHostnameVerification() throws Exception {
        var system = KeyStore.getInstance(KeyStore.getDefaultType());
        try (var stream = Files.newInputStream(Path.of(System.getProperty("java.home"), "lib", "security", "cacerts"))) {
            system.load(stream, "changeit".toCharArray());
        }
        var certificate = system.getCertificate(system.aliases().nextElement());
        Path bundle = directory.resolve("root.pem");
        Files.writeString(bundle, "-----BEGIN CERTIFICATE-----\n" +
                Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(certificate.getEncoded()) +
                "\n-----END CERTIFICATE-----\n");
        var context = SsoHttpClientFactory.loadTrust(bundle);
        assertEquals("TLS", context.getProtocol());
        assertNotSame(SSLContext.getDefault(), context);
    }

    @Test
    void missingDirectoryEmptyAndMalformedBundlesFailClosed() throws Exception {
        assertThrows(IllegalStateException.class, () -> SsoHttpClientFactory.loadTrust(directory.resolve("missing.pem")));
        assertThrows(IllegalStateException.class, () -> SsoHttpClientFactory.loadTrust(directory));
        Path bundle = directory.resolve("invalid.pem");
        Files.writeString(bundle, "");
        assertThrows(IllegalStateException.class, () -> SsoHttpClientFactory.loadTrust(bundle));
        Files.writeString(bundle, "invalid certificate");
        assertThrows(IllegalStateException.class, () -> SsoHttpClientFactory.loadTrust(bundle));
    }
}
