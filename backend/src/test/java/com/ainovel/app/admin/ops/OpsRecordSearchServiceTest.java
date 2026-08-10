package com.ainovel.app.admin.ops;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRecordSearchServiceTest {

    @Test
    void searchFailureUsesFixedResponseAndSafeThrowable() {
        OpsRecordSearchService service = new OpsRecordSearchService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "elasticsearchHosts", "http://[endpoint-token-secret");
        ReflectionTestUtils.setField(service, "username", "elastic");
        ReflectionTestUtils.setField(service, "password", "password-secret");
        ReflectionTestUtils.setField(service, "indexPrefix", "private-path");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OpsRecordSearchService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        OpsRecordSearchService.SearchResult result;
        try {
            result = service.search(
                    OpsRecordFileSink.AUDIT_RECORD, null, null, null, null, null,
                    null, null, 0, 20
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertFalse(result.available());
        assertEquals("Elasticsearch query failed", result.message());
        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertTrue(event.getFormattedMessage().contains("errorType=IllegalArgumentException"));
        assertFalse(event.getFormattedMessage().contains("endpoint-token-secret"));
        assertFalse(event.getFormattedMessage().contains("password-secret"));
        assertFalse(event.getFormattedMessage().contains("private-path"));
        assertNotNull(event.getThrowableProxy());
        assertNull(event.getThrowableProxy().getMessage());
    }
}
