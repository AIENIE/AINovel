package com.ainovel.app.v2;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2ManuscriptBranch;
import com.ainovel.app.v2.model.V2ManuscriptVersion;
import com.ainovel.app.v2.repo.V2ManuscriptVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.ActiveProfiles("test")
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({com.ainovel.app.manuscript.ManuscriptContentService.class,V2VersionPersistenceService.class, V2Json.class, JsonColumnCodec.class, V2BranchIntegrityTest.Beans.class})
class V2BranchIntegrityTest {
    private static final String SCENE = "11111111-1111-1111-1111-111111111111";
    @PersistenceContext EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired V2VersionPersistenceService versions;
    @Autowired V2ManuscriptVersionRepository versionRepository;
    @Autowired FaultyMapper mapper;

    @TestConfiguration
    static class Beans {
        @Bean FaultyMapper objectMapper() { return new FaultyMapper(); }
    }

    static class FaultyMapper extends ObjectMapper {
        boolean failSections;
        FaultyMapper() { findAndRegisterModules(); }
        @Override public String writeValueAsString(Object value) throws JsonProcessingException {
            if (failSections && value instanceof Map<?, ?> map && map.containsKey(SCENE)) {
                throw new JsonMappingException((java.io.Closeable) null, "private manuscript details");
            }
            return super.writeValueAsString(value);
        }
    }

    @AfterEach void resetFault() { mapper.failSections = false; }

    @Test
    void mergePreservesUncheckpointedMainInSameTransaction() {
        Fixture fixture = fixture();
        Map<String, Object> merged = versions.mergeBranch(fixture.manuscript(), fixture.user(), fixture.branchId(), V2BranchRequests.MergeBranch.defaults());
        inTransaction(() -> {
            assertEquals(json("branch"), em.find(Manuscript.class, fixture.manuscript().getId()).getSectionsJson());
            V2ManuscriptVersion backup = em.find(V2ManuscriptVersion.class, (UUID) merged.get("backupVersionId"));
            assertEquals(json("main latest saved edit"), backup.getSectionsJson());
            assertEquals(fixture.mainBranchId(), backup.getBranch().getId());
            assertEquals("manual", backup.getSnapshotType());
            assertEquals("merged", em.find(V2ManuscriptBranch.class, fixture.branchId()).getStatus());
            return null;
        });
    }

    @Test
    void sceneSelectionUsesCurrentMainAndDoesNotWriteOnConflict() {
        Fixture fixture = fixture();
        int before = count(fixture);
        Map<String, Object> conflict = versions.mergeBranch(fixture.manuscript(), fixture.user(), fixture.branchId(),
                new V2BranchRequests.MergeBranch(V2BranchRequests.MergeStrategy.SCENE_SELECT, Map.of(), null));
        assertEquals("conflict", conflict.get("status"));
        assertEquals(before, count(fixture));
        assertState(fixture, json("main latest saved edit"), "active");
        versions.mergeBranch(fixture.manuscript(), fixture.user(), fixture.branchId(),
                new V2BranchRequests.MergeBranch(V2BranchRequests.MergeStrategy.SCENE_SELECT,
                        Map.of(SCENE, V2BranchRequests.Resolution.TARGET), null));
        assertState(fixture, json("main latest saved edit"), "merged");
    }

    @Test
    void serializationFailureRollsBackFlushedBackupAndBranchState() {
        Fixture fixture = fixture();
        int before = count(fixture);
        mapper.failSections = true;
        ApiStatusException error = assertThrows(ApiStatusException.class, () -> versions.mergeBranch(
                fixture.manuscript(), fixture.user(), fixture.branchId(), V2BranchRequests.MergeBranch.defaults()));
        mapper.failSections = false;
        assertEquals("STORED_DATA_SERIALIZATION_FAILED", error.getMessage());
        assertEquals(before, count(fixture), "Even the already flushed pre-merge snapshot must roll back");
        assertState(fixture, json("main latest saved edit"), "active");
    }

    @Test
    void malformedSourceDoesNotReplaceMainOrCreateSnapshots() {
        Fixture fixture = fixture();
        inTransaction(() -> {
            var source = versionRepository.findByManuscriptIdAndBranchId(fixture.manuscript().getId(), fixture.branchId());
            source.forEach(version -> version.setSectionsJson("{bad}"));
            return null;
        });
        int before = count(fixture);
        assertThrows(ApiStatusException.class, () -> versions.mergeBranch(fixture.manuscript(), fixture.user(),
                fixture.branchId(), V2BranchRequests.MergeBranch.defaults()));
        assertEquals(before, count(fixture));
        assertState(fixture, json("main latest saved edit"), "active");
    }

    @Test
    void illegalStatusAndDuplicateNameDoNotChangeBranch() {
        Fixture fixture = fixture();
        assertThrows(BusinessException.class, () -> versions.updateBranch(fixture.manuscript().getId(), fixture.branchId(),
                new V2BranchRequests.UpdateBranch(null, null, V2BranchRequests.BranchStatus.MERGED)));
        assertThrows(BusinessException.class, () -> versions.updateBranch(fixture.manuscript().getId(), fixture.branchId(),
                new V2BranchRequests.UpdateBranch("main", null, null)));
        assertState(fixture, json("main latest saved edit"), "active");
    }

    @Test
    void cachedDiffCannotBeReadThroughAnotherManuscript() {
        Fixture owner = fixture();
        Fixture other = fixture();
        var ids = versionRepository.findByManuscriptIdOrderByCreatedAtDesc(owner.manuscript().getId()).stream()
                .map(V2ManuscriptVersion::getId).toList();
        Map<String, Object> first = versions.diff(owner.manuscript(), owner.user(), ids.get(0), ids.get(1));
        assertEquals(first.get("changes"), versions.diff(owner.manuscript(), owner.user(), ids.get(0), ids.get(1)).get("changes"));
        assertThrows(BusinessException.class, () -> versions.diff(other.manuscript(), other.user(), ids.get(0), ids.get(1)));
        assertThrows(BusinessException.class, () -> versions.diff(other.manuscript(), other.user(), ids.get(0),
                versionRepository.findByManuscriptIdOrderByCreatedAtDesc(other.manuscript().getId()).getFirst().getId()));
    }

    private Fixture fixture() {
        return inTransaction(() -> {
            String unique = UUID.randomUUID().toString();
            User user = new User(); user.setUsername(unique); user.setEmail(unique + "@example.com"); user.setPasswordHash("x");
            em.persist(user);
            Story story = new Story(); story.setUser(user); story.setTitle("测试小说"); story.setStatus("draft"); em.persist(story);
            Outline outline = new Outline(); outline.setStory(story); outline.setTitle("大纲"); outline.setContentJson("{\"chapters\":[]}"); em.persist(outline);
            Manuscript manuscript = new Manuscript(); manuscript.setOutline(outline); manuscript.setTitle("正文"); manuscript.setSectionsJson(json("base")); em.persist(manuscript); em.flush();
            versions.ensureMainBranchAndInitialVersion(manuscript, user);
            UUID main = manuscript.getCurrentBranchId();
            UUID branch = (UUID) versions.createBranch(manuscript, user, new V2BranchRequests.CreateBranch("alternative", "", null)).get("id");
            manuscript.setCurrentBranchId(branch); manuscript.setSectionsJson(json("branch"));
            versions.createVersion(manuscript, user, Map.of("label", "branch draft"));
            manuscript.setCurrentBranchId(main); manuscript.setSectionsJson(json("main latest saved edit")); em.flush();
            return new Fixture(manuscript, user, main, branch);
        });
    }

    private int count(Fixture fixture) { return versionRepository.findByManuscriptIdOrderByCreatedAtDesc(fixture.manuscript().getId()).size(); }
    private String json(String text) { return "{\"" + SCENE + "\":\"" + text + "\"}"; }
    private void assertState(Fixture fixture, String sections, String branchState) {
        inTransaction(() -> {
            assertEquals(sections, em.find(Manuscript.class, fixture.manuscript().getId()).getSectionsJson());
            assertEquals(branchState, em.find(V2ManuscriptBranch.class, fixture.branchId()).getStatus());
            return null;
        });
    }
    private <T> T inTransaction(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    private record Fixture(Manuscript manuscript, User user, UUID mainBranchId, UUID branchId) { }
}
