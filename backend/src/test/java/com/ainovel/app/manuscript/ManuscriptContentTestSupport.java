package com.ainovel.app.manuscript;

import com.ainovel.app.common.JsonColumnCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

/** Real legacy storage semantics for manually constructed unit-test services. */
public final class ManuscriptContentTestSupport {
    private ManuscriptContentTestSupport() { }

    public static void injectLegacy(Object service) {
        ReflectionTestUtils.setField(service, "contents", new ManuscriptContentService(
                mock(JdbcTemplate.class), new JsonColumnCodec(new ObjectMapper().findAndRegisterModules()), false));
    }
}
