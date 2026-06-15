package com.ainovel.app.admin;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.MaterialService;
import com.ainovel.app.material.dto.MaterialDto;
import com.ainovel.app.material.dto.MaterialMergeRequest;
import com.ainovel.app.material.dto.MaterialReviewRequest;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Operations", description = "AINovel 业务运营后台接口")
@SecurityRequirement(name = "bearerAuth")
public class AdminOperationsController {
    private final MaterialService materialService;
    private final MaterialRepository materialRepository;
    private final StoryRepository storyRepository;
    private final WorldRepository worldRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final SlopQualityRunRepository slopQualityRunRepository;
    private final PlotQualityRunRepository plotQualityRunRepository;

    public AdminOperationsController(
            MaterialService materialService,
            MaterialRepository materialRepository,
            StoryRepository storyRepository,
            WorldRepository worldRepository,
            ManuscriptRepository manuscriptRepository,
            SlopQualityRunRepository slopQualityRunRepository,
            PlotQualityRunRepository plotQualityRunRepository
    ) {
        this.materialService = materialService;
        this.materialRepository = materialRepository;
        this.storyRepository = storyRepository;
        this.worldRepository = worldRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.slopQualityRunRepository = slopQualityRunRepository;
        this.plotQualityRunRepository = plotQualityRunRepository;
    }

    @GetMapping("/materials/pending")
    @Operation(summary = "待审素材列表")
    public List<MaterialDto> pendingMaterials() {
        return materialService.pending();
    }

    @PostMapping("/materials/{id}/approve")
    @Operation(summary = "通过素材审核")
    public MaterialDto approveMaterial(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) {
        return materialService.review(id, "approve", request);
    }

    @PostMapping("/materials/{id}/reject")
    @Operation(summary = "驳回素材审核")
    public MaterialDto rejectMaterial(@PathVariable UUID id, @RequestBody MaterialReviewRequest request) {
        return materialService.review(id, "reject", request);
    }

    @PostMapping("/materials/duplicates")
    @Operation(summary = "素材重复检测")
    public List<Map<String, Object>> duplicateMaterials() {
        return materialService.findDuplicates();
    }

    @PostMapping("/materials/merge")
    @Operation(summary = "合并素材")
    public MaterialDto mergeMaterial(@RequestBody MaterialMergeRequest request) {
        return materialService.merge(request);
    }

    @GetMapping("/materials/{id}/citations")
    @Operation(summary = "素材引用查询")
    public List<Map<String, Object>> materialCitations(@PathVariable UUID id) {
        return materialService.citations(id);
    }

    @GetMapping("/assets/summary")
    @Transactional(readOnly = true)
    @Operation(summary = "创作资产汇总")
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

    @GetMapping("/assets/stories")
    @Transactional(readOnly = true)
    @Operation(summary = "故事资产只读列表")
    public List<Map<String, Object>> stories() {
        return storyRepository.findAll().stream()
                .sorted(Comparator.comparing(Story::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(200)
                .map(story -> asset("story", story.getId(), story.getTitle(), story.getStatus(),
                        story.getUser() == null ? "" : story.getUser().getUsername(), story.getUpdatedAt()))
                .toList();
    }

    @GetMapping("/assets/worlds")
    @Transactional(readOnly = true)
    @Operation(summary = "世界观资产只读列表")
    public List<Map<String, Object>> worlds() {
        return worldRepository.findAll().stream()
                .sorted(Comparator.comparing(World::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(200)
                .map(world -> asset("world", world.getId(), world.getName(), world.getStatus(),
                        world.getUser() == null ? "" : world.getUser().getUsername(), world.getUpdatedAt()))
                .toList();
    }

    @GetMapping("/assets/manuscripts")
    @Transactional(readOnly = true)
    @Operation(summary = "稿件资产只读列表")
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

    @GetMapping("/quality/runs")
    @Transactional(readOnly = true)
    @Operation(summary = "质量巡检运行记录")
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
