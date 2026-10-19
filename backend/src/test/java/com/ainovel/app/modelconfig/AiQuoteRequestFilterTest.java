package com.ainovel.app.modelconfig;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiQuoteRequestFilterTest {
    private final AiActionPlanner planner=mock(AiActionPlanner.class);
    private final AiQuoteService quotes=mock(AiQuoteService.class);
    private final CurrentUserResolver users=mock(CurrentUserResolver.class);
    private final ObjectMapper json=new ObjectMapper();
    private final User user=new User();
    private final AiQuoteRequestFilter filter=new AiQuoteRequestFilter(planner,quotes,users,json);
    private MockHttpServletRequest request() {
        var request=new MockHttpServletRequest("POST","/api/v1/ai/chat");request.setContent("{\"messages\":[]}".getBytes(StandardCharsets.UTF_8));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author",null,java.util.List.of()));
        when(planner.candidate("POST","/v1/ai/chat")).thenReturn(true);when(planner.features(eq("/v1/ai/chat"),any())).thenReturn(java.util.List.of("generic.chat"));when(users.require(any())).thenReturn(user);return request;
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();assertThat(AiSelectionContext.current()).isNull();}
    @Test void missingPaidQuoteStopsBeforeControllerMutation() throws Exception {
        var request=request();var response=new MockHttpServletResponse();
        when(quotes.accept(eq(user),eq("/v1/ai/chat"),any(),isNull(),isNull())).thenThrow(new ApiStatusException(HttpStatus.PRECONDITION_REQUIRED,"AI_QUOTE_REQUIRED"));
        filter.doFilter(request,response,(req,res)->{throw new AssertionError("Unconfirmed request reached controller");});
        assertThat(response.getStatus()).isEqualTo(428);assertThat(json.readTree(response.getContentAsByteArray()).path("code").asText()).isEqualTo("AI_QUOTE_REQUIRED");
    }
    @Test void acceptedQuotePreservesBodyAndInstallsOnlyItsFrozenScope() throws Exception {
        var request=request();request.addHeader("X-AI-Quote-Id","quote");request.addHeader("Idempotency-Key","request");
        var snapshot=new AiSelectionContext.Snapshot("quote",Map.of());
        when(quotes.accept(eq(user),eq("/v1/ai/chat"),any(),eq("quote"),eq("request"))).thenReturn(snapshot);
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{
            assertThat(AiSelectionContext.current()).isEqualTo(snapshot);assertThat(new String(req.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).isEqualTo("{\"messages\":[]}");
        });
    }
}
