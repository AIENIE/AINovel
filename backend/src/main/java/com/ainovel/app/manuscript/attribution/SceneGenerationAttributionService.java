package com.ainovel.app.manuscript.attribution;

import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.SafeLogThrowable;
import com.ainovel.app.manuscript.GenerationMode;
import com.ainovel.app.manuscript.attribution.model.SceneGenerationRun;
import com.ainovel.app.manuscript.attribution.repo.SceneGenerationRunRepository;
import com.ainovel.app.manuscript.dto.SceneGenerationFeedbackPatchRequest;
import com.ainovel.app.manuscript.dto.SceneGenerationRunDto;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.v2.model.V2ManuscriptVersion;
import com.ainovel.app.v2.repo.V2ManuscriptVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SceneGenerationAttributionService {
    private static final Logger log = LoggerFactory.getLogger(SceneGenerationAttributionService.class);
    private static final String PROMPT_VERSION = "scene-draft-v2";
    private static final Set<String> ALLOWED_FEEDBACK_TAGS = Set.of(
            "PLOT_CAUSALITY",
            "CHARACTER_MOTIVATION",
            "CONTINUITY_SETTING",
            "VOICE_DIALOGUE",
            "PACING",
            "STYLE_SPECIFICITY",
            "AI_CLICHE",
            "OTHER"
    );

    private final SceneGenerationRunRepository runRepository;
    private final V2ManuscriptVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public SceneGenerationAttributionService(
            SceneGenerationRunRepository runRepository,
            V2ManuscriptVersionRepository versionRepository,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SceneGenerationRunDto recordGeneration(
            Manuscript manuscript,
            UUID sceneId,
            UUID generationVersionId,
            GenerationMode mode,
            Map<String, Object> manifest,
            String generatedContent
    ) {
        List<SceneGenerationRun> previousRuns = runRepository
                .findByManuscriptIdAndSceneIdAndStatusInOrderByCreatedAtDesc(
                        manuscript.getId(),
                        sceneId,
                        liveStatuses()
                );

        String content = safe(generatedContent);
        int generatedCharacters = codePointCount(content);
        Map<String, Object> sanitizedManifest = SceneGenerationManifest.sanitize(manifest);
        SceneGenerationRun run = new SceneGenerationRun();
        run.setManuscript(manuscript);
        run.setSceneId(sceneId);
        run.setGenerationVersionId(generationVersionId);
        run.setCreatedBy(manuscript.getOutline().getStory().getUser());
        run.setPreviousRunId(previousRuns.isEmpty() ? null : previousRuns.getFirst().getId());
        run.setStatus(SceneGenerationRunStatus.GENERATED);
        run.setAttributionStatus(SceneAttributionStatus.READY);
        run.setMode(mode == null ? GenerationMode.FAST.name() : mode.name());
        run.setModelKey(AiModelPolicy.REQUIRED_TEXT_MODEL_KEY);
        run.setPromptVersion(PROMPT_VERSION);
        run.setAttemptCount(positiveInt(sanitizedManifest.get("attemptCount"), 1));
        run.setContextHash(requiredSha256(sanitizedManifest.get("contextHash"), "生成上下文哈希"));
        run.setPromptHash(requiredSha256(sanitizedManifest.get("promptHash"), "生成提示词哈希"));
        run.setContextManifestJson(writeJson(contextManifest(sanitizedManifest)));
        run.setGeneratedContentHash(sha256(content));
        run.setCurrentContentHash(run.getGeneratedContentHash());
        run.setGeneratedCharacters(generatedCharacters);
        run.setCurrentCharacters(generatedCharacters);
        run.setRetainedCharacters(generatedCharacters);
        run.setAddedCharacters(0);
        run.setDeletedCharacters(0);
        run.setRetentionRate(1.0d);
        run.setExactMatch(true);
        run.setDiffJson(writeJson(equalOnlyDiff(generatedCharacters)));
        run.setTagsJson("[]");
        run.setPreferenceConfirmed(false);
        run.setRecomputedAt(Instant.now());
        run = runRepository.saveAndFlush(run);

        for (SceneGenerationRun previous : previousRuns) {
            previous.setStatus(SceneGenerationRunStatus.SUPERSEDED);
            previous.setSupersededByRunId(run.getId());
        }
        if (!previousRuns.isEmpty()) {
            runRepository.saveAllAndFlush(previousRuns);
        }
        return toDto(run);
    }

    @Transactional
    public List<SceneGenerationRunDto> listRuns(UUID manuscriptId, UUID sceneId, int requestedLimit) {
        int limit = Math.max(1, Math.min(50, requestedLimit));
        SceneGenerationRun liveRun = latestLiveRun(manuscriptId, sceneId);
        if (liveRun != null) {
            String currentContent = readSections(liveRun.getManuscript().getSectionsJson())
                    .getOrDefault(sceneId.toString(), "");
            String currentHash = sha256(currentContent);
            if (!currentHash.equals(liveRun.getCurrentContentHash())) {
                markPending(liveRun, currentContent, Instant.now());
                runRepository.saveAndFlush(liveRun);
            }
            if (liveRun.getAttributionStatus() == SceneAttributionStatus.PENDING) {
                try {
                    recompute(liveRun, currentContent);
                    runRepository.saveAndFlush(liveRun);
                } catch (RuntimeException ex) {
                    log.error(
                            "scene_generation_attribution_recompute_failed manuscriptId={} sceneId={} runId={} action=query_recompute pendingPreserved=true errorType={}",
                            manuscriptId,
                            sceneId,
                            liveRun.getId(),
                            ex.getClass().getSimpleName(),
                            SafeLogThrowable.stackOnly(ex)
                    );
                }
            }
        }
        List<SceneGenerationRun> runs = runRepository.findByManuscriptIdAndSceneIdOrderByCreatedAtDesc(
                manuscriptId,
                sceneId,
                PageRequest.of(0, limit)
        );
        return runs.stream().map(this::toDto).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recomputeAfterCommit(UUID manuscriptId, UUID sceneId, String currentContent) {
        SceneGenerationRun run = latestLiveRun(manuscriptId, sceneId);
        if (run == null) {
            return;
        }
        markPending(run, currentContent, Instant.now());
        runRepository.saveAndFlush(run);
        try {
            recompute(run, currentContent);
            runRepository.saveAndFlush(run);
        } catch (RuntimeException ex) {
            log.error(
                    "scene_generation_attribution_recompute_failed manuscriptId={} sceneId={} runId={} action=post_commit_recompute pendingPreserved=true errorType={}",
                    manuscriptId,
                    sceneId,
                    run.getId(),
                    ex.getClass().getSimpleName(),
                    SafeLogThrowable.stackOnly(ex)
            );
        }
    }

    @Transactional
    public SceneGenerationRunDto patchFeedback(
            UUID manuscriptId,
            UUID sceneId,
            UUID runId,
            SceneGenerationFeedbackPatchRequest request
    ) {
        if (request == null) {
            throw new BusinessException("反馈内容不能为空");
        }
        SceneGenerationRun run = runRepository.findByIdAndManuscriptIdAndSceneId(runId, manuscriptId, sceneId)
                .orElseThrow(() -> new BusinessException("生成记录不存在"));
        if (request.tags() != null) {
            run.setTagsJson(writeJson(normalizeTags(request.tags())));
        }
        if (request.note() != null) {
            String note = request.note().trim();
            if (codePointCount(note) > 500) {
                throw new BusinessException("反馈备注不能超过 500 字");
            }
            run.setNote(note.isEmpty() ? null : note);
        }
        if (request.preferenceConfirmed() != null) {
            run.setPreferenceConfirmed(request.preferenceConfirmed());
            run.setPreferenceConfirmedAt(request.preferenceConfirmed() ? Instant.now() : null);
        }
        if (run.isPreferenceConfirmed() && readTags(run.getTagsJson()).isEmpty()
                && (run.getNote() == null || run.getNote().isBlank())) {
            throw new BusinessException("确认长期偏好证据前，请至少填写一个反馈标签或备注");
        }
        return toDto(runRepository.saveAndFlush(run));
    }

    @EventListener
    @Transactional
    public void markReverted(ManuscriptRollbackEvent event) {
        SceneGenerationRun targetRun = runRepository
                .findByManuscriptIdAndGenerationVersionId(event.manuscriptId(), event.targetVersionId())
                .orElse(null);
        Set<UUID> affectedSceneIds = new LinkedHashSet<>(event.changedSceneIds());
        if (targetRun != null) {
            affectedSceneIds.add(targetRun.getSceneId());
        }
        if (affectedSceneIds.isEmpty()) {
            return;
        }

        Map<UUID, SceneGenerationRun> changedRuns = new LinkedHashMap<>();
        List<SceneGenerationRun> liveRuns = runRepository.findByManuscriptIdAndStatusInOrderByCreatedAtDesc(
                event.manuscriptId(),
                liveStatuses()
        );
        for (SceneGenerationRun run : liveRuns) {
            if (!affectedSceneIds.contains(run.getSceneId())
                    || (targetRun != null && targetRun.getId().equals(run.getId()))) {
                continue;
            }
            run.setStatus(SceneGenerationRunStatus.REVERTED);
            changedRuns.put(run.getId(), run);
        }
        if (targetRun != null) {
            V2ManuscriptVersion targetVersion = versionRepository
                    .findByManuscriptIdAndId(event.manuscriptId(), event.targetVersionId())
                    .orElseThrow(() -> new BusinessException("回滚目标生成快照不存在"));
            String restoredContent = readSections(targetVersion.getSectionsJson())
                    .getOrDefault(targetRun.getSceneId().toString(), "");
            recompute(targetRun, restoredContent);
            targetRun.setSupersededByRunId(null);
            changedRuns.put(targetRun.getId(), targetRun);
        }
        if (!changedRuns.isEmpty()) {
            runRepository.saveAll(changedRuns.values());
            log.info(
                    "scene_generation_runs_rollback_reconciled manuscriptId={} targetVersionId={} affectedSceneCount={} changedRunCount={} targetGenerationRestored={}",
                    event.manuscriptId(),
                    event.targetVersionId(),
                    affectedSceneIds.size(),
                    changedRuns.size(),
                    targetRun != null
            );
        }
    }

    private void recompute(SceneGenerationRun run, String currentContent) {
        V2ManuscriptVersion generationVersion = versionRepository
                .findByManuscriptIdAndId(run.getManuscript().getId(), run.getGenerationVersionId())
                .orElseThrow(() -> new BusinessException("生成快照不存在，无法重算编辑归因"));
        Map<String, String> generatedSections = readSections(generationVersion.getSectionsJson());
        String generatedContent = safe(generatedSections.get(run.getSceneId().toString()));
        UnicodeCodePointDiff.Result result = UnicodeCodePointDiff.calculate(generatedContent, safe(currentContent));

        run.setGeneratedContentHash(sha256(generatedContent));
        run.setCurrentContentHash(sha256(safe(currentContent)));
        run.setGeneratedCharacters(result.beforeCodePoints());
        run.setCurrentCharacters(result.afterCodePoints());
        run.setRetainedCharacters(result.retainedCodePoints());
        run.setAddedCharacters(result.insertedCodePoints());
        run.setDeletedCharacters(result.deletedCodePoints());
        run.setRetentionRate(result.retainedRatio());
        run.setExactMatch(result.exactMatch());
        run.setDiffJson(writeJson(toStoredDiff(result.operations())));
        run.setAttributionStatus(SceneAttributionStatus.READY);
        run.setStatus(result.exactMatch() && run.getFirstEditedAt() == null
                ? SceneGenerationRunStatus.GENERATED
                : SceneGenerationRunStatus.EDITED);
        run.setRecomputedAt(Instant.now());
    }

    private SceneGenerationRunDto toDto(SceneGenerationRun run) {
        boolean recalculationPending = run.getAttributionStatus() == SceneAttributionStatus.PENDING;
        return new SceneGenerationRunDto(
                run.getId(),
                run.getManuscript().getId(),
                run.getSceneId(),
                run.getCreatedBy().getId(),
                run.getMode() == null ? null : run.getMode().toLowerCase(Locale.ROOT),
                run.getStatus(),
                run.getModelKey(),
                run.getPromptVersion(),
                run.getAttemptCount(),
                run.getContextHash(),
                readMap(run.getContextManifestJson()),
                run.getGenerationVersionId(),
                run.getPreviousRunId(),
                run.getFirstEditedAt(),
                run.getLastEditedAt(),
                recalculationPending ? null : run.getAddedCharacters(),
                recalculationPending ? null : run.getDeletedCharacters(),
                recalculationPending ? null : run.getRetentionRate(),
                recalculationPending,
                readTags(run.getTagsJson()),
                run.getNote(),
                run.isPreferenceConfirmed(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }

    private List<StoredDiffOperation> toStoredDiff(List<UnicodeCodePointDiff.Operation> operations) {
        List<StoredDiffOperation> result = new ArrayList<>(operations.size());
        for (UnicodeCodePointDiff.Operation operation : operations) {
            result.add(new StoredDiffOperation(
                    operation.type().name(),
                    operation.beforeStart(),
                    operation.afterStart(),
                    operation.codePointCount(),
                    operation.type() == UnicodeCodePointDiff.OperationType.EQUAL ? null : operation.text()
            ));
        }
        return List.copyOf(result);
    }

    private List<StoredDiffOperation> equalOnlyDiff(int codePointCount) {
        if (codePointCount == 0) {
            return List.of();
        }
        return List.of(new StoredDiffOperation("EQUAL", 0, 0, codePointCount, null));
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags.size() > 8) {
            throw new BusinessException("反馈标签不能超过 8 个");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String rawTag : tags) {
            String tag = rawTag == null ? "" : rawTag.trim().toUpperCase(Locale.ROOT);
            if (tag.isEmpty()) {
                throw new BusinessException("反馈标签不能为空");
            }
            if (!ALLOWED_FEEDBACK_TAGS.contains(tag)) {
                throw new BusinessException("不支持的反馈标签：" + tag);
            }
            normalized.add(tag);
        }
        return List.copyOf(normalized);
    }

    private Map<String, Object> contextManifest(Map<String, Object> manifest) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("promptVersion", PROMPT_VERSION);
        context.put("tokenBudget", nonNegativeInt(manifest.get("tokenBudget")));
        context.put("tokenUsed", nonNegativeInt(manifest.get("tokenUsed")));
        Object sources = manifest.get("sources");
        context.put("sources", sources instanceof List<?> list ? List.copyOf(list) : List.of());
        return Map.copyOf(context);
    }

    private SceneGenerationRun latestLiveRun(UUID manuscriptId, UUID sceneId) {
        return runRepository
                .findByManuscriptIdAndSceneIdAndStatusInOrderByCreatedAtDesc(
                        manuscriptId,
                        sceneId,
                        liveStatuses()
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void markPending(SceneGenerationRun run, String currentContent, Instant editedAt) {
        String content = safe(currentContent);
        run.setAttributionStatus(SceneAttributionStatus.PENDING);
        run.setCurrentContentHash(sha256(content));
        run.setCurrentCharacters(codePointCount(content));
        run.setRecomputedAt(null);
        if (run.getFirstEditedAt() == null) {
            run.setFirstEditedAt(editedAt);
        }
        run.setLastEditedAt(editedAt);
    }

    private Map<String, String> readSections(String json) {
        try {
            return objectMapper.readValue(safeJson(json, "{}"), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BusinessException("生成快照正文损坏，无法重算编辑归因", ex);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(safeJson(json, "{}"), new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Unable to read scene generation manifest", ex);
            return Map.of();
        }
    }

    private List<String> readTags(String json) {
        try {
            return objectMapper.readValue(safeJson(json, "[]"), new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Unable to read scene generation feedback tags", ex);
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException("无法保存生成归因数据", ex);
        }
    }

    private static String safeJson(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int codePointCount(String value) {
        String text = safe(value);
        return text.codePointCount(0, text.length());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String text(Object value, String fallback) {
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString().trim();
    }

    private static int positiveInt(Object value, int fallback) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        try {
            int parsed = value == null ? fallback : Integer.parseInt(value.toString());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int nonNegativeInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.toString()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String requiredSha256(Object value, String label) {
        String hash = text(value, null);
        if (hash == null || !hash.matches("[0-9a-fA-F]{64}")) {
            throw new BusinessException(label + "缺失或格式错误");
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private static List<SceneGenerationRunStatus> liveStatuses() {
        return List.of(SceneGenerationRunStatus.GENERATED, SceneGenerationRunStatus.EDITED);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate content hash", ex);
        }
    }

    private record StoredDiffOperation(
            String type,
            int beforeStart,
            int afterStart,
            int codePointCount,
            String text
    ) {}
}
