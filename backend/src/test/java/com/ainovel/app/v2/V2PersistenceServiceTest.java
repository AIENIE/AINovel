package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.ainovel.app.config.AppTimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({
        V2ContextPersistenceService.class,
        V2ModelPersistenceService.class,
        V2WorkspacePersistenceService.class,
        V2AnalysisPersistenceService.class,
        V2VersionPersistenceService.class,
        V2ExportPersistenceService.class,
        AppTimeProvider.class,
        V2PersistenceServiceTest.TestBeans.class
})
class V2PersistenceServiceTest {
    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Autowired
    private V2ContextPersistenceService contextService;

    @Autowired
    private V2ModelPersistenceService modelService;

    @Autowired
    private V2WorkspacePersistenceService workspaceService;

    @Autowired
    private V2AnalysisPersistenceService analysisService;

    @Autowired
    private V2VersionPersistenceService versionService;

    @Autowired
    private V2ExportPersistenceService exportService;

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void contextRecordsShouldPersistAndReloadFromRepositories() {
        User user = persistUser("v2-context");
        Story story = persistStory(user);

        Map<String, Object> entry = contextService.createLorebook(user, story, Map.of(
                "displayName", "青云山",
                "category", "location",
                "content", "宗门所在灵山",
                "keywords", List.of("青云", "灵山"),
                "priority", 9
        ));
        Map<String, Object> extraction = contextService.createExtraction(story, Map.of(
                "text", "林烬在青云山发现石碑",
                "entityName", "林烬",
                "entityType", "character"
        ));

        entityManager.flush();
        entityManager.clear();

        assertEquals("青云山", contextService.listLorebook(story.getId()).get(0).get("displayName"));
        assertEquals("林烬", contextService.listExtractions(story.getId()).get(0).get("entityName"));
        assertEquals(entry.get("id"), contextService.listLorebook(story.getId()).get(0).get("id"));
        assertEquals(extraction.get("id"), contextService.listExtractions(story.getId()).get(0).get("id"));
    }

    @Test
    void modelPreferencesAndUsageShouldPersist() {
        User user = persistUser("v2-model");
        Story story = persistStory(user);

        Map<String, Object> model = modelService.listModels().get(0);
        modelService.savePreference(user, "draft_generation", (java.util.UUID) model.get("id"));
        modelService.logUsage(user, story.getId(), (java.util.UUID) model.get("id"),
                "draft_generation", 100, 200, 300, true, null);

        entityManager.flush();
        entityManager.clear();

        assertEquals(1, modelService.listPreferences(user).size());
        assertEquals(1, modelService.usageDetails(user, 10).size());
        assertEquals(200, ((Number) modelService.usageSummary(user).get("totalOutputTokens")).intValue());
    }

    @Test
    void workspaceStateShouldPersist() {
        User user = persistUser("v2-workspace");
        Story story = persistStory(user);

        workspaceService.createLayout(user, Map.of("name", "沉浸布局", "layout", Map.of("center", 60), "isActive", true));
        Map<String, Object> goal = workspaceService.createGoal(user, story, Map.of(
                "goalType", "daily_words",
                "targetValue", 1200
        ));
        workspaceService.updateShortcuts(user, List.of(Map.of("action", "focus_mode", "shortcut", "F11")));

        entityManager.flush();
        entityManager.clear();

        assertEquals("沉浸布局", workspaceService.listLayouts(user).get(0).get("name"));
        assertEquals(goal.get("id"), workspaceService.listGoals(user).get(0).get("id"));
        assertTrue(workspaceService.listShortcuts(user).stream()
                .anyMatch(item -> "F11".equals(item.get("shortcut"))));
    }

    @Test
    void analysisJobsReportsAndIssuesShouldPersist() {
        User user = persistUser("v2-analysis");
        Story story = persistStory(user);

        V2AnalysisDtos.AnalysisJobResponse job = analysisService.createAnalysisJob(user, story, Map.of("focus", "continuity"), "continuity_check");
        analysisService.createContinuityIssue(story.getId(), job.resultReference(), "先后顺序冲突");

        entityManager.flush();
        entityManager.clear();

        assertEquals(1, analysisService.listJobs(story.getId()).size());
        assertEquals(1, analysisService.listReports(story.getId()).size());
        assertEquals(1, analysisService.listContinuityIssues(story.getId()).size());
        assertEquals("continuity", analysisService.listReports(story.getId()).get(0).analysis().focus());
        assertEquals("timeline_error", analysisService.listContinuityIssues(story.getId()).get(0).issueType());
    }

    @Test
    void versionsBranchesAndAutoSaveShouldPersist() {
        User user = persistUser("v2-version");
        Story story = persistStory(user);
        ManuscriptFixture fixture = persistManuscript(story, "{\"scene-1\":\"hello\"}");

        versionService.ensureMainBranchAndInitialVersion(fixture.manuscript, user);
        fixture.manuscript.setSectionsJson("{\"scene-1\":\"checkpoint\"}");
        Map<String, Object> checkpoint = versionService.createVersion(fixture.manuscript, user, Map.of("label", "checkpoint"));
        Map<String, Object> branch = versionService.createBranch(fixture.manuscript, user, Map.of(
                "name", "alt",
                "sourceVersionId", checkpoint.get("id")
        ));
        versionService.updateAutoSave(user, Map.of("autoSaveIntervalSeconds", 60, "maxAutoVersions", 12));

        entityManager.flush();
        entityManager.clear();

        assertEquals(3, versionService.listVersions(fixture.manuscript.getId()).size());
        assertEquals("alt", versionService.listBranches(fixture.manuscript.getId()).stream()
                .filter(row -> branch.get("id").equals(row.get("id")))
                .findFirst()
                .orElseThrow()
                .get("name"));
        assertEquals(60, versionService.getAutoSave(user).get("autoSaveIntervalSeconds"));
    }

    @Test
    void exportTemplatesAndJobsShouldPersist() {
        User user = persistUser("v2-export");
        Story story = persistStory(user);
        ManuscriptFixture fixture = persistManuscript(story, "{\"scene-1\":\"hello\"}");

        Map<String, Object> template = exportService.createTemplate(user, Map.of(
                "name", "TXT",
                "format", "txt",
                "config", Map.of("includeMetadata", false)
        ));
        Map<String, Object> job = exportService.createJob(user, fixture.manuscript, Map.of(
                "format", "txt",
                "templateId", template.get("id")
        ), "hello.txt", "text/plain; charset=UTF-8");

        entityManager.flush();
        entityManager.clear();

        assertTrue(exportService.listTemplates(user).stream().anyMatch(row -> template.get("id").equals(row.get("id"))));
        assertEquals(job.get("id"), exportService.listJobs(fixture.manuscript.getId()).get(0).get("id"));
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("x");
        user.setRemoteUid((long) username.hashCode());
        entityManager.persist(user);
        return user;
    }

    private Story persistStory(User user) {
        Story story = new Story();
        story.setUser(user);
        story.setTitle("雨城疑案");
        story.setGenre("悬疑");
        story.setTone("冷峻");
        story.setStatus("draft");
        entityManager.persist(story);
        return story;
    }

    private ManuscriptFixture persistManuscript(Story story, String sectionsJson) {
        com.ainovel.app.story.model.Outline outline = new com.ainovel.app.story.model.Outline();
        outline.setStory(story);
        outline.setTitle("主线");
        outline.setContentJson("{\"chapters\":[]}");
        entityManager.persist(outline);

        com.ainovel.app.manuscript.model.Manuscript manuscript = new com.ainovel.app.manuscript.model.Manuscript();
        manuscript.setOutline(outline);
        manuscript.setTitle("雨城疑案正文");
        manuscript.setSectionsJson(sectionsJson);
        entityManager.persist(manuscript);
        return new ManuscriptFixture(manuscript);
    }

    private record ManuscriptFixture(com.ainovel.app.manuscript.model.Manuscript manuscript) {
    }
}
