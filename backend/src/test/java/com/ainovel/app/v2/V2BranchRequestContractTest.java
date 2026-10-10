package com.ainovel.app.v2;

import com.ainovel.app.common.GlobalExceptionHandler;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V2BranchRequestContractTest {
    private final UUID manuscriptId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private V2VersionPersistenceService versions;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ResourceAccessGuard access = mock(ResourceAccessGuard.class);
        versions = mock(V2VersionPersistenceService.class);
        User user = new User();
        Manuscript manuscript = new Manuscript();
        when(access.currentUser(any())).thenReturn(user);
        when(access.requireOwnedManuscript(manuscriptId, user)).thenReturn(manuscript);
        when(versions.mergeBranch(any(), any(), eq(branchId), any())).thenReturn(Map.of("status", "merged"));
        var controller = new V2VersionController(access, versions);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "manuscriptTransactions", new com.ainovel.app.manuscript.OwnedManuscriptTransactions(access));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"strategy\":null}", "{\"strategy\":42}",
            "{\"strategy\":\"SCENE_SELET\"}", "{\"stratgey\":\"SCENE_SELECT\"}",
            "{\"sceneResolutions\":null}", "{\"sceneResolutions\":[]}",
            "{\"sceneResolutions\":{\"11111111-1111-1111-1111-111111111111\":\"other\"}}",
            "{\"sceneResolutions\":{\"11111111-1111-1111-1111-111111111111\":null}}", "{\"label\":null}"})
    void invalidMergeBodyFailsBeforeService(String body) throws Exception {
        mvc.perform(post(path() + "/" + branchId + "/merge").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions);
    }

    @Test
    void emptyObjectKeepsDocumentedDefault() throws Exception {
        mvc.perform(post(path() + "/" + branchId + "/merge").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(versions).mergeBranch(any(), any(), eq(branchId), eq(V2BranchRequests.MergeBranch.defaults()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":null}", "{\"name\":\" \"}", "{\"status\":null}",
            "{\"status\":\"unexpected\"}", "{\"status\":12}", "{\"description\":null}", "{\"other\":true}"})
    void invalidUpdateBodyFailsBeforeService(String body) throws Exception {
        mvc.perform(put(path() + "/" + branchId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(versions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":123}", "{\"name\":\"x\",\"sourceVersionId\":null}",
            "{\"name\":\"x\",\"sourceVersionId\":\"invalid\"}", "{\"name\":\"x\",\"status\":\"active\"}"})
    void invalidCreateBodyFailsBeforeService(String body) throws Exception {
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(versions);
    }

    private String path() { return "/v2/manuscripts/" + manuscriptId + "/branches"; }
}
