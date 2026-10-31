package com.ainovel.app.quality;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.style.StyleContextProvider;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SlopDiagnosticService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.manuscript.ManuscriptContentService contents;
    @org.springframework.beans.factory.annotation.Autowired
    private QualityPersistenceBoundary boundary;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.narrative.NarrativeContextService isolation;

    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final LocalSlopHeuristics heuristics;
    private final CharacterCardRepository characterCardRepository;
    private final StyleContextProvider styleContextProvider;
    private final SlopQualityRunRepository runRepository;
    private final JsonColumnCodec jsonColumnCodec;

    public SlopDiagnosticService(AiService aiService,
                                 ObjectMapper objectMapper,
                                 LocalSlopHeuristics heuristics,
                                 CharacterCardRepository characterCardRepository,
                                 StyleContextProvider styleContextProvider,
                                 SlopQualityRunRepository runRepository,
                                 JsonColumnCodec jsonColumnCodec) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.heuristics = heuristics;
        this.characterCardRepository = characterCardRepository;
        this.styleContextProvider = styleContextProvider;
        this.runRepository = runRepository;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    public List<SlopQualityRun> listRuns(UUID manuscriptId, UUID sceneId) {
        if (sceneId != null) {
            return runRepository.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(manuscriptId, sceneId);
        }
        return runRepository.findTop20ByManuscriptIdOrderByCreatedAtDesc(manuscriptId);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private SlopInputAdmission admission;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.v2.V2VersionPersistenceService versions;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.manuscript.repo.ManuscriptRepository manuscripts;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.quality.language.LanguageQualityService language;

    public SlopEvidence.ReviewData freeze(User user,Manuscript manuscript,UUID sceneId) {
        return boundary.snapshot(manuscript,current->freezeCurrent(user,current,sceneId)).data();
    }
    private SlopEvidence.ReviewData freezeCurrent(User user,Manuscript m,UUID sceneId) {
        versions.ensureGenerationBaseline(m,user); manuscripts.flush();
        var p=com.ainovel.app.quality.language.LanguageProjection.of(contents.readScene(m,sceneId));
        var d=material(buildRequest(m,sceneId),p);
        UUID snapshot=UUID.fromString(versions.createVersion(m,user,Map.of("snapshotType","slop_check","label","Slop 检查来源")).get("id").toString());
        d.source=new com.ainovel.app.quality.language.LanguageDtos.Source(m.getCurrentBranchId(),m.getVersion(),snapshot,p.version(),p.htmlHash(),p.textHash(),
                com.ainovel.app.quality.language.LanguageProjection.hash(writeJson(d.request)),com.ainovel.app.quality.language.LanguageStandard.VERSION);
        return d;
    }
    private SlopEvidence.ReviewData material(SlopQualityRequest request,com.ainovel.app.quality.language.LanguageProjection.Projection p) {
        var local=heuristics.evaluate(SlopHeuristicInput.from(request,p.text()));
        var d=new SlopEvidence.ReviewData(); d.projection=p; d.localSignals=local.issues(); d.localRisk=local.overallRiskScore(); d.shadowHits=local.shadowHits();
        d.request=SlopEvidence.request(request,local,"slop.diagnose",objectMapper);
        return d;
    }
    public SlopQualityRun analyzeScene(User user,Manuscript manuscript,UUID sceneId) {
        return analyzeFrozen(user,manuscript,sceneId,freeze(user,manuscript,sceneId));
    }
    public SlopQualityRun analyzeFrozen(User user,Manuscript manuscript,UUID sceneId,SlopEvidence.ReviewData frozen) {
        if(frozen==null || frozen.version!=1 || !SlopEvidence.VERSION.equals(frozen.promptVersion) || frozen.source==null)
            throw new com.ainovel.app.ai.AiResultUncertainException(new IllegalStateException("SLOP_SOURCE_MISSING_REDIAGNOSE"));
        var run=new SlopQualityRun(); run.setStoryId(manuscript.getOutline().getStory().getId());
        run.setManuscriptId(manuscript.getId()); run.setSceneId(sceneId); run.setAnalysisMode("manual_scene");
        run.setCandidateTextHash(frozen.projection.textHash()); run.setAcceptedTextHash(frozen.projection.textHash());
        run.setSourceTextHash(frozen.projection.textHash()); run.setTextConversionVersion(frozen.projection.version());
        return evaluate(user,run,frozen);
    }
    private SlopQualityRun evaluate(User user,SlopQualityRun run,SlopEvidence.ReviewData d) {
        int risk=0;
        try {
            admission.requireFits(d.request);
            d.raw=aiService.chat(user,d.request).content();
            var parsed=SlopEvidence.parse(d.raw,d.projection,objectMapper);
            d.findings=parsed.findings(); d.coverage=parsed.complete()?"COMPLETE":"PARTIAL"; d.reason=parsed.summary(); risk=parsed.risk();
        } catch(RuntimeException e) {
            if(e instanceof com.ainovel.app.modelconfig.AiPaymentRequiredException || e instanceof com.ainovel.app.ai.AiResultUncertainException) throw e;
            d.coverage=e instanceof SlopInputAdmission.InputLimit?"INPUT_LIMIT":"FAILED";
            d.reason=e instanceof SlopInputAdmission.InputLimit?e.getMessage():"语义检查失败或返回格式无效；仅保留本地观察，不能视为通过。";
        }
        run.setOverallRiskScore(risk); run.setMaxSeverity(SlopEvidence.severity(risk));
        run.setStatus(!d.coverage.equals("COMPLETE")?SlopQualityStatus.DEGRADED:risk>=70?SlopQualityStatus.ACCEPTED_WITH_ISSUES:SlopQualityStatus.ACCEPTED);
        run.setRiskLabel(d.coverage.equals("COMPLETE")?label(risk):"unchecked"); run.setSummary(truncate(d.reason,800));
        run.setSafeClaim(d.coverage.equals("COMPLETE")?"仅汇总有效、已确认的文本问题；文风建议及本地命中不等于错误。":"正文未完成检查；保留的本地观察不能作为低风险或通过结论。");
        run.setEvidenceLevel(d.coverage.equals("COMPLETE")?"E2":"E1");
        run.setModuleScoresJson(writeJson(SlopQualitySignals.empty().withShadowHits(d.shadowHits).moduleScores())); run.setAlternativeExplanationsJson("[]"); run.setRevisionPrioritiesJson("[]"); run.setRewriteTasksJson("[]");
        run.setReviewContextJson(writeJson(d));
        return com.ainovel.app.aioperation.AiOperationExecutionContext.complete(()->runRepository.save(run),saved->Map.of("resourceId",saved.getId()));
    }
    @Transactional
    public com.ainovel.app.aioperation.AiOperationDtos.Accepted suggest(User user,UUID manuscriptId,UUID runId,UUID issueId) {
        var run=runRepository.findById(runId).filter(r->r.getManuscriptId().equals(manuscriptId)).orElseThrow(()->new BusinessException("Slop 报告不存在"));
        var d=review(run);
        if(d==null || d.source==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"旧报告缺少可靠来源，请重新诊断");
        // Import is a source-bound bridge, not another language diagnosis or another provider call.
        UUID reportId=language.importSlop(user,manuscriptId,run.getSceneId(),runId,d);
        d.languageReportId=reportId; run.setReviewContextJson(writeJson(d)); runRepository.save(run);
        return language.suggest(user,manuscriptId,reportId,issueId.toString());
    }
    private SlopEvidence.ReviewData review(SlopQualityRun run) {
        try { return run.getReviewContextJson()==null?null:objectMapper.readValue(run.getReviewContextJson(),SlopEvidence.ReviewData.class); }
        catch(Exception e) { throw new IllegalStateException("SLOP_INVALID_SNAPSHOT",e); }
    }

    private SlopQualityRequest buildRequest(Manuscript manuscript, UUID sceneId) {
        Outline outline = manuscript.getOutline();
        Story story = outline.getStory();
        Map<String, String> sections = contents.readAll(manuscript);
        SceneContext scene = resolveScene(outline, sceneId);
        if(isolation.enabled(manuscript)) {
            var p=isolation.preview(story.getUser(),manuscript.getId(),manuscript.getCurrentBranchId(),sceneId,
                    com.ainovel.app.narrative.NarrativeContextDtos.View.SCENE,null,3500);
            return new SlopQualityRequest(story.getId(),manuscript.getId(),sceneId,"当前作品","未指定","沉浸、连贯","当前章节","当前场景","",
                    p.content(),"","",stripHtml(sections.get(sceneId.toString())),"h2_diagnosis");
        }
        return new SlopQualityRequest(
                story.getId(),
                manuscript.getId(),
                sceneId,
                safe(story.getTitle(), "未命名故事"),
                safe(story.getGenre(), "未指定"),
                safe(story.getTone(), "沉浸、连贯"),
                scene.chapterTitle(),
                scene.sceneTitle(),
                scene.sceneSummary(),
                previousContext(scene, sections),
                characterContext(characterCardRepository.findByStory(story)),
                styleContextProvider.buildSlopContext(story),
                stripHtml(sections.get(sceneId.toString()))
        );
    }

    private SceneContext resolveScene(Outline outline, UUID sceneId) {
        Map<String, Object> root = readObjectMap(outline.getContentJson());
        List<Map<String, Object>> chapters = listOfMap(root.get("chapters"));
        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            Map<String, Object> chapter = chapters.get(chapterIndex);
            List<Map<String, Object>> scenes = listOfMap(chapter.get("scenes"));
            List<UUID> previousSceneIds = new ArrayList<>();
            for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
                Map<String, Object> scene = scenes.get(sceneIndex);
                UUID id = uuid(scene.get("id"));
                if (id != null && id.equals(sceneId)) {
                    return new SceneContext(
                            safe(str(chapter.get("title"), ""), "未命名章节"),
                            safe(str(scene.get("title"), ""), "未命名场景"),
                            safe(str(scene.get("summary"), ""), ""),
                            previousSceneIds
                    );
                }
                if (id != null) {
                    previousSceneIds.add(id);
                }
            }
        }
        throw new BusinessException("场景不存在，无法进行文本 slop 诊断");
    }

    private String previousContext(SceneContext scene, Map<String, String> sections) {
        if (scene.previousSceneIds().isEmpty()) {
            return "暂无可用前文。";
        }
        StringBuilder builder = new StringBuilder();
        int kept = 0;
        for (int i = scene.previousSceneIds().size() - 1; i >= 0 && kept < 2; i--) {
            String plain = stripHtml(sections.get(scene.previousSceneIds().get(i).toString()));
            if (plain.isBlank()) {
                continue;
            }
            builder.append("前文片段 ").append(kept + 1).append("：").append(truncate(plain, 600)).append('\n');
            kept++;
        }
        return builder.length() == 0 ? "暂无可用前文。" : builder.toString();
    }

    private String characterContext(List<CharacterCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return "暂无角色卡。";
        }
        StringBuilder builder = new StringBuilder();
        for (CharacterCard card : cards) {
            builder.append("- ").append(safe(card.getName(), "角色"))
                    .append("：").append(safe(card.getSynopsis(), ""))
                    .append(" 背景：").append(truncate(safe(card.getDetails(), ""), 180))
                    .append(" 关系：").append(truncate(safe(card.getRelationships(), ""), 160))
                    .append('\n');
        }
        return builder.toString();
    }

    private String writeJson(Object value) { return jsonColumnCodec.writeRequired(value); }
    private Map<String,Object> readObjectMap(String value) { return jsonColumnCodec.readRequired(value,new TypeReference<Map<String,Object>>() {}); }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> listOfMap(Object value) { if(value==null)return List.of(); if(!(value instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof Map)))throw new BusinessException("数组项目应为对象"); return (List<Map<String,Object>>)(List<?>)list; }
    private String label(int risk) { return risk>=85?"critical":risk>=70?"high":risk>=40?"medium":"low"; }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String stripHtml(String html) {
        return com.ainovel.app.quality.language.LanguageProjection.of(html).text();
    }


    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safe(String value) {
        return safe(value, "未指定");
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private record SceneContext(
            String chapterTitle,
            String sceneTitle,
            String sceneSummary,
            List<UUID> previousSceneIds
    ) {
    }
}
