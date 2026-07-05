package com.ainovel.app.admin;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminOperationsQueryService {
    private final MaterialRepository materialRepository;
    private final StoryRepository storyRepository;
    private final WorldRepository worldRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final SlopQualityRunRepository slopQualityRunRepository;
    private final PlotQualityRunRepository plotQualityRunRepository;

    public AdminOperationsQueryService(
            MaterialRepository materialRepository,
            StoryRepository storyRepository,
            WorldRepository worldRepository,
            ManuscriptRepository manuscriptRepository,
            SlopQualityRunRepository slopQualityRunRepository,
            PlotQualityRunRepository plotQualityRunRepository
    ) {
        this.materialRepository = materialRepository;
        this.storyRepository = storyRepository;
        this.worldRepository = worldRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.slopQualityRunRepository = slopQualityRunRepository;
        this.plotQualityRunRepository = plotQualityRunRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> assetSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stories", storyRepository.count());
        result.put("worlds", worldRepository.count());
        result.put("manuscripts", manuscriptRepository.count());
        result.put("materials", materialRepository.count());
        result.put("pendingMaterials", materialRepository.countByStatusIgnoreCase("pending"));
        result.put("highRiskQualityRuns", slopQualityRunRepository.countByOverallRiskScoreGreaterThanEqual(70)
                + plotQualityRunRepository.countByOverallRiskScoreGreaterThanEqual(70));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> stories() {
        return storyRepository.findAll().stream()
                .sorted(Comparator.comparing(Story::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(200)
                .map(story -> asset("story", story.getId(), story.getTitle(), story.getStatus(),
                        story.getUser() == null ? "" : story.getUser().getUsername(), story.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> worlds() {
        return worldRepository.findAll().stream()
                .sorted(Comparator.comparing(World::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(200)
                .map(world -> asset("world", world.getId(), world.getName(), world.getStatus(),
                        world.getUser() == null ? "" : world.getUser().getUsername(), world.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> manuscripts() {
        return manuscriptRepository.findAll().stream()
                .sorted(Comparator.comparing(Manuscript::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(200)
                .map(manuscript -> {
                    Story story = manuscript.getOutline() == null ? null : manuscript.getOutline().getStory();
                    return asset("manuscript", manuscript.getId(), manuscript.getTitle(), "active",
                            story == null || story.getUser() == null ? "" : story.getUser().getUsername(),
                            manuscript.getUpdatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> qualityRuns() {
        List<Map<String, Object>> slopRuns = slopQualityRunRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::quality)
                .toList();
        List<Map<String, Object>> plotRuns = plotQualityRunRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::quality)
                .toList();
        return java.util.stream.Stream.concat(slopRuns.stream(), plotRuns.stream())
                .sorted((left, right) -> compareCreatedAt(right.get("createdAt"), left.get("createdAt")))
                .limit(200)
                .toList();
    }

    private Map<String, Object> asset(String type, UUID id, String title, String status, String owner, Instant updatedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("id", id);
        item.put("title", title == null || title.isBlank() ? "(未命名)" : title);
        item.put("status", status == null || status.isBlank() ? "unknown" : status);
        item.put("owner", owner);
        item.put("updatedAt", updatedAt);
        return item;
    }

    private Map<String, Object> quality(SlopQualityRun run) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", "slop");
        item.put("id", run.getId());
        item.put("storyId", run.getStoryId());
        item.put("manuscriptId", run.getManuscriptId());
        item.put("sceneId", run.getSceneId());
        item.put("status", run.getStatus() == null ? "" : run.getStatus().name());
        item.put("maxSeverity", run.getMaxSeverity() == null ? "" : run.getMaxSeverity().name());
        item.put("overallRiskScore", run.getOverallRiskScore());
        item.put("resolved", run.isRevised());
        item.put("summary", run.getSummary());
        item.put("createdAt", run.getCreatedAt());
        return item;
    }

    private Map<String, Object> quality(PlotQualityRun run) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", "plot");
        item.put("id", run.getId());
        item.put("storyId", run.getStoryId());
        item.put("manuscriptId", run.getManuscriptId());
        item.put("sceneId", run.getSceneId());
        item.put("chapterTitle", run.getChapterTitle());
        item.put("sceneTitle", run.getSceneTitle());
        item.put("status", run.getStatus() == null ? "" : run.getStatus().name());
        item.put("maxSeverity", run.getMaxSeverity() == null ? "" : run.getMaxSeverity().name());
        item.put("overallRiskScore", run.getOverallRiskScore());
        item.put("resolved", run.isRevisionApplied());
        item.put("summary", run.getSummary());
        item.put("createdAt", run.getCreatedAt());
        return item;
    }

    private int compareCreatedAt(Object left, Object right) {
        if (left instanceof Instant l && right instanceof Instant r) {
            return l.compareTo(r);
        }
        if (left instanceof Instant) {
            return 1;
        }
        if (right instanceof Instant) {
            return -1;
        }
        return 0;
    }
}
