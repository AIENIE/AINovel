package com.ainovel.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigCorsTest {
    @Test
    void defaultsIncludeActiveLocalAndProductionDomainsWithLegacyCompatibility() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", SecurityConfig.DEFAULT_ALLOWED_ORIGINS);
        ReflectionTestUtils.setField(config, "allowedMethods", "GET,POST");
        ReflectionTestUtils.setField(config, "allowedHeaders", "Authorization,Content-Type");

        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v2/models"));

        assertNotNull(cors);
        assertEquals(true, cors.getAllowCredentials());
        assertTrue(cors.getAllowedOrigins().contains("https://localainovel.testhut.top"));
        assertTrue(cors.getAllowedOrigins().contains("https://ainovel.testhut.top"));
        assertTrue(cors.getAllowedOrigins().contains("https://ainovel.seekerhut.com"));
        // Retained only as an explicit compatibility origin, not an active entry point.
        assertTrue(cors.getAllowedOrigins().contains("https://ainovel.aienie.com"));
    }
}
