package com.ainovel.app.workflow;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.user.User;
import com.ainovel.app.workflow.dto.CreationWorkflowDtos;
import com.ainovel.app.workflow.repo.CreationWorkflowRunRepository;
import com.ainovel.app.workflow.repo.AsyncJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import({GuidedCreationWorkflowService.class, GuidedCreationJobService.class,
        GuidedCreationJsonSupport.class, JsonColumnCodec.class, ObjectMapper.class, GuidedCreationRuntime.class})
class GuidedCreationPersistenceTest {
    @Autowired EntityManager em;
    @Autowired GuidedCreationWorkflowService service;
    @Autowired CreationWorkflowRunRepository runs;
    @Autowired AsyncJobRepository jobs;
    @MockBean GuidedCreationMaterializer materializer;
    @MockBean GuidedCreationOutlineJobSupport outlineSupport;
    @MockBean GuidedCreationJobWorker worker;
    @MockBean(name = "guidedCreationExecutor") Executor executor;

    private User detachedUser() {
        User user = new User();
        String suffix = java.util.UUID.randomUUID().toString();
        user.setUsername("author-" + suffix); user.setEmail(suffix + "@example.test");
        user.setPasswordHash("test-only"); user.getRoles().add("ROLE_USER");
        em.persist(user); em.flush(); em.clear();
        return user;
    }
    @Test void createsManualDraftForDetachedPrincipalAndReloads() {
        var response = service.create(detachedUser(), new CreationWorkflowDtos.CreateRunRequest("明日来信", "悬疑", "克制", 3, false));
        assertNotNull(response.id()); assertNotNull(response.createdAt());
        em.clear();
        assertEquals("明日来信", runs.findWithUserById(response.id()).orElseThrow().getSeedIdea());
        verifyNoInteractions(executor);
    }
    @Test void automaticTaskDispatchesOnlyAfterCommit() {
        var response = service.create(detachedUser(), new CreationWorkflowDtos.CreateRunRequest("自动故事", null, null, 3, true));
        assertNotNull(response.activeJob());
        verifyNoInteractions(executor);
        TestTransaction.flagForCommit(); TestTransaction.end();
        verify(executor, times(1)).execute(any(Runnable.class));
    }
    @Test void rollbackRemovesDraftAndJobWithoutDispatch() {
        var response = service.create(detachedUser(), new CreationWorkflowDtos.CreateRunRequest("回滚故事", null, null, 3, true));
        var jobId = response.activeJob().id();
        TestTransaction.flagForRollback(); TestTransaction.end();
        assertFalse(runs.existsById(response.id())); assertFalse(jobs.existsById(jobId));
        verifyNoInteractions(executor);
    }
}
