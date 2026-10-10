package com.ainovel.app.material.evidence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

class MaterialMutationControllerTest {
    @Test
    void invalidUtf8AndOversizedFilesAreRejectedBeforeAnyMutation() {
        var mutations = mock(MaterialMutationService.class);
        var controller = new MaterialMutationController(mutations, mock(CurrentUserResolver.class));
        assertEquals(
                400,
                assertThrows(
                                ApiStatusException.class,
                                () ->
                                        controller.upload(
                                                null,
                                                new MockMultipartFile(
                                                        "file",
                                                        "原文.txt",
                                                        "text/plain",
                                                        new byte[] {(byte) 0xc3, 0x28}),
                                                "intent"))
                        .getStatus()
                        .value());
        assertEquals(
                413,
                assertThrows(
                                ApiStatusException.class,
                                () ->
                                        controller.upload(
                                                null,
                                                new MockMultipartFile(
                                                        "file",
                                                        "原文.txt",
                                                        "text/plain",
                                                        new byte[2_097_153]),
                                                "intent"))
                        .getStatus()
                        .value());
        verifyNoInteractions(mutations);
    }

    @Test
    void utf8OriginalAndStableIntentReachTheTransactionalUploadTogether() throws Exception {
        var mutations = mock(MaterialMutationService.class);
        var users = mock(CurrentUserResolver.class);
        var user = new User();
        when(users.require(null)).thenReturn(user);
        var controller = new MaterialMutationController(mutations, users);
        String raw = "😀雨桥\r\n原文缩进  保留。";
        controller.upload(
                null,
                new MockMultipartFile("file", "原文.TXT", "", raw.getBytes(StandardCharsets.UTF_8)),
                "original-intent");
        verify(mutations).upload(user, "原文.TXT", raw, "original-intent");
    }
}
