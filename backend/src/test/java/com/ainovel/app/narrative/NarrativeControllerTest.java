package com.ainovel.app.narrative;

import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NarrativeControllerTest {
    @Test void writesRequireIdempotencyAndExpectedVersionsAndReadsBindScopedFilters() throws Exception {
        var access = mock(ResourceAccessGuard.class); var service = mock(NarrativeService.class);
        var approvals = mock(NarrativeApprovalCoordinator.class); var user = new User();
        when(access.currentUser(any())).thenReturn(user);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new NarrativeController(access, service, approvals))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        UUID manuscript = UUID.randomUUID(), branch = UUID.randomUUID(), scene = UUID.randomUUID();
        String base = "/v2/manuscripts/" + manuscript + "/branches/" + branch + "/narrative";
        String body = "{\"sceneId\":\"" + scene + "\",\"expectedManuscriptVersion\":3,\"expectedCanonRevision\":0}";
        mvc.perform(post(base + "/scene-approvals").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post(base + "/scene-approvals").header("Idempotency-Key", "test").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approvals);
        mvc.perform(post(base + "/scene-approvals").header("Idempotency-Key", "test").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(approvals).approve(eq(user), eq(manuscript), eq(branch), eq(new NarrativeDtos.ApprovalRequest(scene, 3L, 0L)), eq("test"));
        mvc.perform(get(base + "/state").param("sceneId", scene.toString()).param("kind", "BELIEF").param("canonRevision", "2"))
                .andExpect(status().isOk());
        verify(service).state(user, manuscript, branch, scene, null, NarrativeDtos.Kind.BELIEF, null, 2L);
    }
}
