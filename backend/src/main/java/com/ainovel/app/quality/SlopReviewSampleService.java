package com.ainovel.app.quality;

import com.ainovel.app.admin.dto.SlopReviewSampleCreateRequest;
import com.ainovel.app.admin.dto.SlopReviewSampleDto;
import com.ainovel.app.admin.dto.SlopReviewSampleUpdateRequest;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.model.SlopReviewSample;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.quality.repo.SlopReviewSampleRepository;
import com.ainovel.app.story.model.Story;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SlopReviewSampleService {
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_SLOP_RUN = "SLOP_RUN";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final SlopReviewSampleRepository sampleRepository;
    private final SlopQualityRunRepository runRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final LocalSlopHeuristics heuristics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlopReviewSampleService(
            SlopReviewSampleRepository sampleRepository,
            SlopQualityRunRepository runRepository,
            ManuscriptRepository manuscriptRepository,
            LocalSlopHeuristics heuristics
    ) {
        this.sampleRepository = sampleRepository;
        this.runRepository = runRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.heuristics = heuristics;
    }

    @Transactional(readOnly = true)
    public List<SlopReviewSampleDto> list(String status, String sourceType, String evidenceLevel) {
        List<SlopReviewSample> samples = parseStatus(status)
                .map(sampleRepository::findTop200ByStatusOrderByCreatedAtDesc)
                .orElseGet(sampleRepository::findTop200ByOrderByCreatedAtDesc);
        String normalizedSource = normalizeOptional(sourceType).toUpperCase();
        String normalizedEvidence = normalizeEvidenceLevel(evidenceLevel, "");
        return samples.stream()
                .filter(sample -> normalizedSource.isBlank() || normalizedSource.equals(sample.getSourceType()))
                .filter(sample -> normalizedEvidence.isBlank() || normalizedEvidence.equals(sample.getExpectedEvidenceLevel()))
                .sorted(Comparator.comparing(SlopReviewSample::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(200)
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SlopReviewSampleDto createManual(SlopReviewSampleCreateRequest request, String actor) {
        SlopReviewSample sample = new SlopReviewSample();
        sample.setSourceType(SOURCE_MANUAL);
        sample.setSampleId(blankTo(request.sampleId(), "MANUAL-" + UUID.randomUUID()));
        sample.setText(requireText(request.text()));
        sample.setGenre(normalizeOptional(request.genre()));
        sample.setTone(normalizeOptional(request.tone()));
        sample.setCharacterContext(normalizeOptional(request.characterContext()));
        sample.setStyleContext(normalizeOptional(request.styleContext()));
        sample.setExpectedEvidenceLevel(normalizeEvidenceLevel(request.expectedEvidenceLevel(), "E1"));
        sample.setExpectedRequiresAiReview(Boolean.TRUE.equals(request.expectedRequiresAiReview()));
        sample.setReviewerNote(normalizeOptional(request.reviewerNote()));
        sample.setCreatedBy(actorName(actor));
        sample.setStatus(SlopReviewSampleStatus.PENDING);
        applyObservedSignals(sample);
        return toDto(sampleRepository.save(sample));
    }

    @Transactional
    public SlopReviewSampleDto createFromRun(UUID runId, String actor) {
        return sampleRepository.findBySourceTypeAndSourceRunId(SOURCE_SLOP_RUN, runId)
                .map(this::toDto)
                .orElseGet(() -> createNewFromRun(runId, actor));
    }

    @Transactional
    public SlopReviewSampleDto update(UUID id, SlopReviewSampleUpdateRequest request, String actor) {
        SlopReviewSample sample = sampleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("样本不存在"));
        if (request.expectedEvidenceLevel() != null && !request.expectedEvidenceLevel().isBlank()) {
            sample.setExpectedEvidenceLevel(normalizeEvidenceLevel(request.expectedEvidenceLevel(), "E1"));
        }
        if (request.expectedRequiresAiReview() != null) {
            sample.setExpectedRequiresAiReview(request.expectedRequiresAiReview());
        }
        if (request.reviewerNote() != null) {
            sample.setReviewerNote(normalizeOptional(request.reviewerNote()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            sample.setStatus(parseStatus(request.status()).orElseThrow(() -> new IllegalArgumentException("审核状态无效")));
            sample.setReviewedBy(actorName(actor));
            sample.setReviewedAt(Instant.now());
        }
        applyObservedSignals(sample);
        return toDto(sampleRepository.save(sample));
    }

    private SlopReviewSampleDto createNewFromRun(UUID runId, String actor) {
        SlopQualityRun run = runRepository.findById(runId).orElseThrow(() -> new IllegalArgumentException("质量记录不存在"));
        Manuscript manuscript = manuscriptRepository.findWithStoryById(run.getManuscriptId())
                .orElseThrow(() -> new IllegalArgumentException("稿件不存在"));
        Story story = manuscript.getOutline() == null ? null : manuscript.getOutline().getStory();

        SlopReviewSample sample = new SlopReviewSample();
        sample.setSourceType(SOURCE_SLOP_RUN);
        sample.setSourceRunId(runId);
        sample.setStoryId(run.getStoryId());
        sample.setManuscriptId(run.getManuscriptId());
        sample.setSceneId(run.getSceneId());
        sample.setSampleId("RUN-" + runId);
        sample.setText(sceneText(manuscript, run.getSceneId()));
        sample.setGenre(story == null ? "" : normalizeOptional(story.getGenre()));
        sample.setTone(story == null ? "" : normalizeOptional(story.getTone()));
        sample.setExpectedEvidenceLevel(normalizeEvidenceLevel(run.getEvidenceLevel(), "E1"));
        sample.setExpectedRequiresAiReview(runRequiresReview(run));
        sample.setCreatedBy(actorName(actor));
        sample.setStatus(SlopReviewSampleStatus.PENDING);
        applyObservedSignals(sample);
        return toDto(sampleRepository.save(sample));
    }

    private void applyObservedSignals(SlopReviewSample sample) {
        SlopHeuristicResult result = heuristics.evaluate(new SlopHeuristicInput(
                sample.getText(),
                "",
                sample.getGenre(),
                sample.getTone(),
                sample.getChapterTitle(),
                sample.getSceneTitle(),
                sample.getCharacterContext(),
                sample.getStyleContext()
        ));
        SlopQualitySignals signals = SlopQualitySignals.fromIssues(
                result.overallRiskScore(),
                result.maxSeverity(),
                result.issues()
        );
        sample.setObservedEvidenceLevel(signals.evidenceLevel());
        sample.setObservedRequiresAiReview(result.requiresAiReview());
        sample.setObservedRiskScore(result.overallRiskScore());
        sample.setObservedMaxSeverity(result.maxSeverity().name());
        sample.setMatchesExpected(sample.getExpectedEvidenceLevel().equals(signals.evidenceLevel())
                && sample.isExpectedRequiresAiReview() == result.requiresAiReview());
    }

    private String sceneText(Manuscript manuscript, UUID sceneId) {
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        String html = sections.getOrDefault(sceneId.toString(), "");
        return requireText(stripHtml(html));
    }

    private Map<String, String> readSectionMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String stripHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return HTML_TAG_PATTERN.matcher(text)
                .replaceAll("\n")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean runRequiresReview(SlopQualityRun run) {
        if (run.getOverallRiskScore() >= SlopHeuristicPolicy.defaultPolicy().aiReviewRiskThreshold()) {
            return true;
        }
        return run.getMaxSeverity() == SlopSeverity.HIGH || run.getMaxSeverity() == SlopSeverity.BLOCKING;
    }

    private java.util.Optional<SlopReviewSampleStatus> parseStatus(String raw) {
        String value = normalizeOptional(raw);
        if (value.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(SlopReviewSampleStatus.valueOf(value.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private String normalizeEvidenceLevel(String value, String fallback) {
        String normalized = normalizeOptional(value).toUpperCase();
        return switch (normalized) {
            case "E1", "E2", "E3", "E4" -> normalized;
            default -> fallback;
        };
    }

    private String requireText(String value) {
        String text = normalizeOptional(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException("样本文本不能为空");
        }
        return text;
    }

    private String blankTo(String value, String fallback) {
        String text = normalizeOptional(value);
        return text.isBlank() ? fallback : text;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private String actorName(String actor) {
        return blankTo(actor, "admin");
    }

    private SlopReviewSampleDto toDto(SlopReviewSample sample) {
        String text = sample.getText() == null ? "" : sample.getText();
        return new SlopReviewSampleDto(
                sample.getId(),
                sample.getSourceType(),
                sample.getSourceRunId(),
                sample.getStoryId(),
                sample.getManuscriptId(),
                sample.getSceneId(),
                sample.getSampleId(),
                text,
                text.length() <= 120 ? text : text.substring(0, 120),
                sample.getGenre(),
                sample.getTone(),
                sample.getChapterTitle(),
                sample.getSceneTitle(),
                sample.getCharacterContext(),
                sample.getStyleContext(),
                sample.getExpectedEvidenceLevel(),
                sample.isExpectedRequiresAiReview(),
                sample.getObservedEvidenceLevel(),
                sample.isObservedRequiresAiReview(),
                sample.getObservedRiskScore(),
                sample.getObservedMaxSeverity(),
                sample.isMatchesExpected(),
                sample.getStatus() == null ? "" : sample.getStatus().name(),
                sample.getReviewerNote(),
                sample.getCreatedBy(),
                sample.getReviewedBy(),
                sample.getReviewedAt(),
                sample.getCreatedAt(),
                sample.getUpdatedAt()
        );
    }
}
