package com.ainovel.app.quality;

import com.ainovel.app.admin.dto.SlopReviewSampleCreateRequest;
import com.ainovel.app.admin.dto.SlopReviewSampleDto;
import com.ainovel.app.admin.dto.SlopReviewSampleAiReviewMatrixDto;
import com.ainovel.app.admin.dto.SlopReviewSampleImportRequest;
import com.ainovel.app.admin.dto.SlopReviewSampleImportResultDto;
import com.ainovel.app.admin.dto.SlopReviewSampleMismatchDto;
import com.ainovel.app.admin.dto.SlopReviewSamplePolicyDto;
import com.ainovel.app.admin.dto.SlopReviewSampleReportDto;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SlopReviewSampleService {
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_IMPORT = "MANUAL_IMPORT";
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

    @Transactional(readOnly = true)
    public SlopReviewSampleReportDto report() {
        List<SlopReviewSample> samples = sampleRepository.findAll();
        List<SlopReviewSample> reviewed = samples.stream()
                .filter(this::isReviewed)
                .toList();
        Map<String, Long> expectedCounts = evidenceCounts(reviewed, true);
        Map<String, Long> observedCounts = evidenceCounts(reviewed, false);
        SlopReviewSampleAiReviewMatrixDto aiReviewMatrix = aiReviewMatrix(reviewed);
        SlopHeuristicPolicy policy = SlopHeuristicPolicy.defaultPolicy();
        return new SlopReviewSampleReportDto(
                samples.size(),
                reviewed.size(),
                samples.stream().filter(sample -> sample.getStatus() == SlopReviewSampleStatus.PENDING).count(),
                samples.stream().filter(sample -> sample.getStatus() == SlopReviewSampleStatus.APPROVED).count(),
                samples.stream().filter(sample -> sample.getStatus() == SlopReviewSampleStatus.REJECTED).count(),
                samples.stream().filter(sample -> sample.getStatus() == SlopReviewSampleStatus.NEEDS_DISCUSSION).count(),
                reviewed.stream().filter(SlopReviewSample::isMatchesExpected).count(),
                reviewed.stream().filter(sample -> !sample.isMatchesExpected()).count(),
                reviewed.stream().filter(this::isHighRisk).count(),
                expectedCounts,
                observedCounts,
                evidenceMatrix(reviewed),
                aiReviewMatrix,
                new SlopReviewSamplePolicyDto(
                        policy.singleWeakSignalRisk(),
                        policy.expectedStyleWeakSignalRisk(),
                        policy.mediumDensityRisk(),
                        policy.highDensityRisk(),
                        policy.aiReviewRiskThreshold(),
                        policy.genericPhraseMediumDensityCount(),
                        policy.genericPhraseHighDensityCount()
                ),
                reviewed.stream()
                        .filter(sample -> !sample.isMatchesExpected())
                        .sorted(Comparator.comparing(SlopReviewSample::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(20)
                        .map(this::toMismatchDto)
                        .toList()
        );
    }

    @Transactional
    public SlopReviewSampleImportResultDto importSamples(SlopReviewSampleImportRequest request, String actor) {
        List<Map<String, Object>> rows = parseImportRows(request == null ? "" : request.content());
        List<String> errors = new ArrayList<>();
        List<SlopReviewSampleDto> imported = new ArrayList<>();
        Set<String> seenSampleIds = new LinkedHashSet<>();
        int skipped = 0;
        int rowNumber = 0;
        for (Map<String, Object> row : rows) {
            rowNumber++;
            try {
                String sampleId = blankTo(stringValue(row.get("sampleId")), "IMPORT-" + UUID.randomUUID());
                if (!seenSampleIds.add(sampleId) || sampleRepository.existsBySampleId(sampleId)) {
                    skipped++;
                    continue;
                }
                SlopReviewSample sample = new SlopReviewSample();
                sample.setSourceType(SOURCE_IMPORT);
                sample.setSampleId(sampleId);
                sample.setText(requireText(stringValue(row.get("text"))));
                sample.setGenre(normalizeOptional(stringValue(row.get("genre"))));
                sample.setTone(normalizeOptional(stringValue(row.get("tone"))));
                sample.setCharacterContext(normalizeOptional(stringValue(row.get("characterContext"))));
                sample.setStyleContext(normalizeOptional(stringValue(row.get("styleContext"))));
                sample.setExpectedEvidenceLevel(normalizeEvidenceLevel(stringValue(row.get("expectedEvidenceLevel")), "E1"));
                sample.setExpectedRequiresAiReview(booleanValue(row.get("expectedRequiresAiReview")));
                sample.setReviewerNote(normalizeOptional(stringValue(row.get("reviewerNote"))));
                sample.setCreatedBy(actorName(actor));
                sample.setStatus(SlopReviewSampleStatus.PENDING);
                applyObservedSignals(sample);
                imported.add(toDto(sampleRepository.save(sample)));
            } catch (RuntimeException ex) {
                errors.add("row " + rowNumber + ": " + ex.getMessage());
            }
        }
        return new SlopReviewSampleImportResultDto(imported.size(), skipped, List.copyOf(errors), List.copyOf(imported));
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

    private boolean isReviewed(SlopReviewSample sample) {
        return sample.getStatus() == SlopReviewSampleStatus.APPROVED
                || sample.getStatus() == SlopReviewSampleStatus.REJECTED
                || sample.getStatus() == SlopReviewSampleStatus.NEEDS_DISCUSSION;
    }

    private boolean isHighRisk(SlopReviewSample sample) {
        return sample.getObservedRiskScore() >= 70
                || "HIGH".equals(sample.getObservedMaxSeverity())
                || "BLOCKING".equals(sample.getObservedMaxSeverity());
    }

    private Map<String, Long> evidenceCounts(List<SlopReviewSample> samples, boolean expected) {
        Map<String, Long> counts = emptyEvidenceCounts();
        for (SlopReviewSample sample : samples) {
            String level = normalizeEvidenceLevel(expected ? sample.getExpectedEvidenceLevel() : sample.getObservedEvidenceLevel(), "E1");
            counts.put(level, counts.get(level) + 1);
        }
        return counts;
    }

    private Map<String, Map<String, Long>> evidenceMatrix(List<SlopReviewSample> samples) {
        Map<String, Map<String, Long>> matrix = new LinkedHashMap<>();
        for (String expected : List.of("E1", "E2", "E3", "E4")) {
            matrix.put(expected, emptyEvidenceCounts());
        }
        for (SlopReviewSample sample : samples) {
            String expected = normalizeEvidenceLevel(sample.getExpectedEvidenceLevel(), "E1");
            String observed = normalizeEvidenceLevel(sample.getObservedEvidenceLevel(), "E1");
            Map<String, Long> row = matrix.get(expected);
            row.put(observed, row.get(observed) + 1);
        }
        return matrix;
    }

    private Map<String, Long> emptyEvidenceCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String level : List.of("E1", "E2", "E3", "E4")) {
            counts.put(level, 0L);
        }
        return counts;
    }

    private SlopReviewSampleAiReviewMatrixDto aiReviewMatrix(List<SlopReviewSample> samples) {
        int tt = 0;
        int tf = 0;
        int ft = 0;
        int ff = 0;
        for (SlopReviewSample sample : samples) {
            if (sample.isExpectedRequiresAiReview() && sample.isObservedRequiresAiReview()) tt++;
            else if (sample.isExpectedRequiresAiReview()) tf++;
            else if (sample.isObservedRequiresAiReview()) ft++;
            else ff++;
        }
        return new SlopReviewSampleAiReviewMatrixDto(tt, tf, ft, ff);
    }

    private SlopReviewSampleMismatchDto toMismatchDto(SlopReviewSample sample) {
        String text = sample.getText() == null ? "" : sample.getText();
        return new SlopReviewSampleMismatchDto(
                sample.getId(),
                sample.getSampleId(),
                sample.getExpectedEvidenceLevel(),
                sample.getObservedEvidenceLevel(),
                sample.isExpectedRequiresAiReview(),
                sample.isObservedRequiresAiReview(),
                sample.getObservedRiskScore(),
                sample.getStatus() == null ? "" : sample.getStatus().name(),
                text.length() <= 120 ? text : text.substring(0, 120)
        );
    }

    private List<Map<String, Object>> parseImportRows(String rawContent) {
        String content = normalizeOptional(rawContent);
        if (content.isBlank()) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        try {
            if (content.startsWith("[")) {
                return objectMapper.readValue(content, new TypeReference<>() {});
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String line : content.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isBlank()) {
                    rows.add(objectMapper.readValue(trimmed, new TypeReference<>() {}));
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException("导入内容必须是 JSONL 或 JSON 数组");
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
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
