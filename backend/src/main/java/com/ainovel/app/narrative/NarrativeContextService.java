package com.ainovel.app.narrative;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.narrative.NarrativeContextDtos.*;
import static com.ainovel.app.narrative.NarrativeText.*;
import static com.ainovel.app.manuscript.context.SceneDraftContextCompiler.estimateTokens;
import static com.ainovel.app.manuscript.context.SceneDraftContextCompiler.queryTerms;
import static com.ainovel.app.manuscript.context.SceneDraftContextCompiler.historyRelevance;

/** All projections are built from scoped, author-confirmed data before relevance/budget selection. */
@Service
public class NarrativeContextService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.manuscript.ManuscriptContentService contents;
    @org.springframework.beans.factory.annotation.Autowired
    private NarrativeGenerationCandidateRepository candidates;
    @Transactional
    public List<NarrativeGenerationCandidate> candidates(User user, UUID manuscriptId, UUID branchId) {
        state(user,manuscriptId,branchId);
        return candidates.findTop20ByManuscriptIdAndBranchIdOrderByCreatedAtDesc(manuscriptId,branchId);
    }
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.v2.repo.V2ManuscriptBranchRepository branches;
    public static final String VERSION = "scene-isolation-h2-v4";
    private final ResourceAccessGuard access;
    private final NarrativeService narrative;
    private final NarrativeContextSettingsRepository settings;
    private final NarrativeContextRevisionRepository revisions;
    private final ManuscriptRepository manuscripts;
    private final CharacterCardRepository characters;
    private final ObjectMapper json;
    private final Validator validator;

    public NarrativeContextService(ResourceAccessGuard access, NarrativeService narrative,
            NarrativeContextSettingsRepository settings, NarrativeContextRevisionRepository revisions,
            ManuscriptRepository manuscripts, CharacterCardRepository characters, ObjectMapper json, Validator validator) {
        this.access=access; this.narrative=narrative; this.settings=settings; this.revisions=revisions;
        this.manuscripts=manuscripts; this.characters=characters; this.json=json; this.validator=validator;
    }

    public boolean enabled(Manuscript manuscript) {
        return settings.findById(manuscript.getOutline().getStory().getId()).map(NarrativeContextSettings::isEnabled).orElse(false);
    }

    @Transactional
    public void reconcileManuscript(UUID id) {
        manuscripts.findWithStoryById(id).ifPresent(m -> branches.findByManuscriptId(id).forEach(b -> {
            if (revisions.findFirstByBranchIdOrderByRevisionDesc(b.getId()).isPresent())
                state(m.getOutline().getStory().getUser(),id,b.getId());
        }));
    }

    @Transactional
    public State state(User user, UUID manuscriptId, UUID branchId) {
        Manuscript m = access.requireOwnedManuscript(manuscriptId, user);
        var ledger = narrative.state(user, manuscriptId, branchId, null, null, null, null, null);
        NarrativeContextSettings setting = settings.findById(m.getOutline().getStory().getId()).orElse(null);
        NarrativeContextRevision latest = revisions.findFirstByBranchIdOrderByRevisionDesc(branchId).orElse(null);
        Document document = latest == null ? null : read(latest.getDocumentJson(), Document.class);
        if (document != null) {
            Document checked = invalidate(user, m, branchId, document, ledger);
            if (!checked.equals(document)) {
                latest = append(branchId, latest.getRevision()+1, checked, null, null, null);
                document = checked;
            }
        }
        return new State(setting != null && setting.isEnabled(), setting == null ? 0 : setting.getRevision(),
                latest == null ? 0 : latest.getRevision(), m.getVersion(), ledger.canonRevision(), document);
    }

    @Transactional
    public State update(User user, UUID manuscriptId, UUID branchId, Update request, String key) {
        Manuscript m = access.requireOwnedManuscript(manuscriptId, user);
        manuscripts.findByIdForUpdate(manuscriptId).orElseThrow();
        if (key == null || key.isBlank() || key.length()>128) throw invalid("NARRATIVE_IDEMPOTENCY_REQUIRED");
        String requestHash=hash(write(request));
        var replay=revisions.findByBranchIdAndIdempotencyKey(branchId,key).orElse(null);
        if (replay!=null) {
            if (!requestHash.equals(replay.getRequestHash())) throw conflict("NARRATIVE_IDEMPOTENCY_CONFLICT");
            // Ownership and manuscript/branch relationship must still be checked on replays.
            narrative.state(user, manuscriptId, branchId, null, null, null, null, null);
            return read(replay.getReceiptJson(), State.class);
        }
        State before=state(user,manuscriptId,branchId);
        if (!branchId.equals(m.getCurrentBranchId())) throw conflict("NARRATIVE_BRANCH_CHANGED");
        if (request==null || !validator.validate(request).isEmpty()) throw invalid("H2_INVALID_CONFIGURATION");
        if (request.expectedManuscriptVersion()!=before.manuscriptVersion() || request.expectedCanonRevision()!=before.canonRevision()
                || request.expectedRevision()!=before.revision() || request.expectedSettingsRevision()!=before.settingsRevision())
            throw conflict("NARRATIVE_VERSION_CHANGED");
        Document doc=validate(user,m,branchId,request.document(),before.document());
        NarrativeContextSettings setting=settings.findById(m.getOutline().getStory().getId()).orElseGet(() -> {
            var s=new NarrativeContextSettings(); s.setStoryId(m.getOutline().getStory().getId()); return s;
        });
        if (setting.isEnabled()!=request.enabled()) { setting.setEnabled(request.enabled()); setting.setRevision(setting.getRevision()+1); }
        settings.saveAndFlush(setting);
        var row=append(branchId,before.revision()+1,doc,key,requestHash,user.getId());
        State after=new State(setting.isEnabled(),setting.getRevision(),row.getRevision(),m.getVersion(),before.canonRevision(),doc);
        row.setReceiptJson(write(after));
        return after;
    }

    @Transactional
    public List<Map<String,Object>> history(User user, UUID manuscriptId, UUID branchId) {
        state(user,manuscriptId,branchId);
        return revisions.findByBranchIdOrderByRevisionDesc(branchId).stream().map(r -> Map.<String,Object>of(
                "revision",r.getRevision(),"createdAt",r.getCreatedAt(),"document",read(r.getDocumentJson(),Document.class))).toList();
    }

    private Document validate(User user, Manuscript m, UUID branchId, Document doc, Document old) {
        var positions=positions(tree(m.getOutline().getContentJson()));
        Set<UUID> ids=new HashSet<>(); characters.findByStory(m.getOutline().getStory()).forEach(c -> ids.add(c.getId()));
        doc.policy().viewpointByScene().forEach((scene,character) -> {
            if (!positions.containsKey(scene) || !ids.contains(character)) throw invalid("H2_INVALID_VIEWPOINT");
        });
        var ledger=narrative.state(user,m.getId(),branchId,null,null,null,null,null);
        Map<UUID,NarrativeDtos.RecordView> records=new HashMap<>(); ledger.records().forEach(r -> records.put(r.id(),r));
        Set<UUID> unique=new HashSet<>();
        List<Grant> grants=new ArrayList<>();
        for (Grant g:doc.grants()) {
            if (!unique.add(g.id()) || !ids.contains(g.characterId()) || !positions.containsKey(g.fromSceneId())) throw invalid("H2_INVALID_KNOWLEDGE");
            boolean wasStale=old!=null && old.grants().stream().anyMatch(x -> x.id().equals(g.id()) && x.stale());
            if (wasStale || g.stale()) {
                Grant previous=old==null?null:old.grants().stream().filter(x->x.id().equals(g.id())).findFirst().orElse(null);
                if (!g.equals(previous)) throw invalid("H2_STALE_REQUIRES_NEW_DECISION");
                grants.add(g); continue;
            }
            var record=records.get(g.recordId());
            if (record==null || !"CONFIRMED".equals(record.status())) throw invalid("H2_RECORD_UNAVAILABLE");
            if (g.view()!=null && g.view().kind()!=record.assertion().kind()) throw invalid("H2_KNOWLEDGE_KIND_MISMATCH");
            var evidence=narrative.evidence(user,m.getId(),branchId,g.approvalId());
            if (!narrative.approvalCurrent(user,m.getId(),branchId,g.approvalId())) throw conflict("NARRATIVE_SOURCE_STALE");
            int from=positions.get(g.fromSceneId()).index();
            if (from<=evidence.disclosedAt().index() || from<=record.disclosedAt().index()) throw invalid("H2_KNOWLEDGE_BEFORE_EVIDENCE");
            grants.add(new Grant(g.id(),g.recordId(),g.characterId(),g.approvalId(),
                    g.evidence().stream().map(e -> resolve(evidence.blocks(),e)).toList(),g.fromSceneId(),g.uncertainty(),false,g.view()));
        }
        for (Entry e:doc.entries()) {
            if (!unique.add(e.id()) || !positions.containsKey(e.fromSceneId()) || !ids.containsAll(e.characterIds())) throw invalid("H2_INVALID_ENTRY");
            if (e.kind()==EntryKind.READER_HYPOTHESIS && e.dependencyRecordIds().isEmpty()) throw invalid("H2_HYPOTHESIS_EVIDENCE_REQUIRED");
            Entry previous=old==null?null:old.entries().stream().filter(x->x.id().equals(e.id())).findFirst().orElse(null);
            if ((e.stale() || previous!=null && previous.stale()) && !e.equals(previous)) throw invalid("H2_STALE_REQUIRES_NEW_DECISION");
            if (!e.stale()) for (UUID dependency:e.dependencyRecordIds()) {
                var r=records.get(dependency);
                if (r==null || !"CONFIRMED".equals(r.status()) || r.disclosedAt().index()>=positions.get(e.fromSceneId()).index())
                    throw invalid("H2_RECORD_UNAVAILABLE");
            }
        }
        Map<UUID,String> hashes=new HashMap<>();
        positions.forEach((id,p)->hashes.put(id,p.orderHash()));
        return new Document(doc.policy(),List.copyOf(grants),doc.entries(),hashes);
    }

    private Document invalidate(User user, Manuscript m, UUID branch, Document doc, NarrativeDtos.StateView ledger) {
        Set<UUID> active=new HashSet<>(); ledger.records().stream().filter(r->"CONFIRMED".equals(r.status())).forEach(r->active.add(r.id()));
        var positions=positions(tree(m.getOutline().getContentJson()));
        List<Grant> grants=doc.grants().stream().map(g -> {
            boolean stale=g.stale() || !active.contains(g.recordId()) || !positions.containsKey(g.fromSceneId())
                    || !Objects.equals(doc.positionHashes().get(g.fromSceneId()),positions.get(g.fromSceneId()).orderHash())
                    || !narrative.approvalCurrent(user,m.getId(),branch,g.approvalId());
            return new Grant(g.id(),g.recordId(),g.characterId(),g.approvalId(),g.evidence(),g.fromSceneId(),g.uncertainty(),stale,g.view());
        }).toList();
        List<Entry> entries=doc.entries().stream().map(e -> new Entry(e.id(),e.kind(),e.text(),e.fromSceneId(),e.characterIds(),
                e.narratorVisible(),e.dependencyRecordIds(),e.stale() || !positions.containsKey(e.fromSceneId())
                || !Objects.equals(doc.positionHashes().get(e.fromSceneId()),positions.get(e.fromSceneId()).orderHash()) || !active.containsAll(e.dependencyRecordIds()))).toList();
        return new Document(doc.policy(),grants,entries,doc.positionHashes());
    }

    @Transactional
    public Preview preview(User user, UUID manuscriptId, UUID branchId, UUID sceneId, View view, UUID characterId, int requestedBudget) {
        Manuscript m=access.requireOwnedManuscript(manuscriptId,user);
        State state=state(user,manuscriptId,branchId);
        if (!branchId.equals(m.getCurrentBranchId())) throw conflict("NARRATIVE_BRANCH_CHANGED");
        if (state.document()==null) throw invalid("H2_CONFIGURATION_REQUIRED");
        var doc=state.document();
        var positions=positions(tree(m.getOutline().getContentJson()));
        var cutoff=positions.get(sceneId);
        if (cutoff==null) throw invalid("NARRATIVE_SCENE_MISSING");
        Map<UUID,String> names=new LinkedHashMap<>(); characters.findByStory(m.getOutline().getStory()).forEach(c->names.put(c.getId(),c.getName()));
        UUID perspective=view==View.CHARACTER?characterId:doc.policy().viewpointByScene().get(sceneId);
        if (view==View.CHARACTER && !names.containsKey(perspective) || view==View.SCENE && doc.policy().perspective()!=Perspective.OMNISCIENT && !names.containsKey(perspective))
            throw invalid("H2_VIEWPOINT_REQUIRED");
        var ledger=narrative.state(user,manuscriptId,branchId,null,null,null,null,null);
        List<Fragment> eligible=new ArrayList<>(),excluded=new ArrayList<>();
        if (view==View.SCENE) eligible.add(new Fragment("contract","CONTRACT",write(Map.of("perspective",doc.policy().perspective(),
                "allowInner",doc.policy().allowInner(),"viewpoint",perspective==null?"":names.getOrDefault(perspective,""))),"作者叙事约定",false));
        if (view==View.READER) {
            var sections=tree(contents.snapshot(m));
            for (var p:positions.values()) if (p.index()<cutoff.index()) {
                String text=String.join("\n",blocks(sections.path(p.sceneId().toString()).asText()).stream().map(NarrativeDtos.Block::text).toList());
                if (!text.isBlank()) eligible.add(new Fragment(p.sceneId().toString(),"DISCLOSED_TEXT",text,"截止场景之前的已保存正文",false));
            }
        }
        for (var r:ledger.records()) {
            boolean prior=r.disclosedAt().index()<cutoff.index();
            boolean knows=r.assertion().knowledge().stream().anyMatch(k->k.characterId().equals(perspective) && k.view()!=null) || doc.grants().stream().anyMatch(g ->
                    !g.stale() && g.view()!=null && g.recordId().equals(r.id()) && g.characterId().equals(perspective) && positions.get(g.fromSceneId()).index()<=cutoff.index());
            boolean allowed="CONFIRMED".equals(r.status()) && prior && view!=View.READER &&
                    (knows || view==View.SCENE && doc.policy().perspective()==Perspective.OMNISCIENT);
            boolean omniscient=view==View.SCENE && doc.policy().perspective()==Perspective.OMNISCIENT;
            String body=omniscient ? write(Map.of("kind",r.assertion().kind(),"subject",r.assertion().subject(),"statement",r.assertion().statement(),
                    "worldTime",Objects.toString(r.assertion().worldTime(),"未知，不据此推断获知先后"),"disclosedAtIndex",r.disclosedAt().index(),
                    "holder",names.getOrDefault(r.assertion().holderCharacterId(),""),
                    "uncertainty",Objects.toString(r.assertion().uncertainty(),""),"knowledge",knowledge(r,doc,positions,cutoff.index(),names,
                            null))) : write(Map.of("recordId",r.id(),"disclosedAtIndex",r.disclosedAt().index(),
                            "knowledge",knowledge(r,doc,positions,cutoff.index(),names,perspective)));
            (allowed?eligible:excluded).add(new Fragment(r.id().toString(),"RECORD",allowed?body:write(r.assertion()),allowed?"有效证据与人物可用表述匹配":!prior?"截止位置之后":!"CONFIRMED".equals(r.status())?"来源待复核或已替代":"没有已确认的人物可用表述；旧知情可无费用补充",false));
        }
        for (Entry e:doc.entries()) {
            var from=positions.get(e.fromSceneId());
            boolean timing=from!=null && (e.kind()==EntryKind.PLAN?e.fromSceneId().equals(sceneId):from.index()<=cutoff.index());
            boolean allowed=!e.stale() && timing && switch(view) {
                case CHARACTER -> e.kind()==EntryKind.BACKGROUND && e.characterIds().contains(characterId);
                case READER -> false;
                case SCENE -> e.kind()==EntryKind.PLAN || e.kind()==EntryKind.BACKGROUND &&
                        (e.characterIds().contains(perspective) || doc.policy().perspective()==Perspective.OMNISCIENT && e.narratorVisible());
            };
            (allowed?eligible:excluded).add(new Fragment(e.id().toString(),e.kind().name(),e.text(),allowed?(e.kind()==EntryKind.PLAN?"本场作者计划，尚未发生":"作者批准的适用背景"):"适用范围不匹配、待复核或仅为读者假设",false));
        }
        excluded.add(new Fragment("unclassified","LEGACY_SOURCES","", "未分类梗概、标题、章节规划、人物详情、世界备注、Lorebook、风格样文、素材及图关系不进入严格输入",false));
        // Query terms can only come from content already authorized for this projection.
        String query=eligible.stream().filter(f->f.category().equals("PLAN") || f.category().equals("CONTRACT"))
                .map(Fragment::content).reduce("",(a,b)->a+" "+b);
        var terms=queryTerms(query);
        eligible.sort(Comparator.<Fragment>comparingInt(f -> f.category().equals("CONTRACT")?0:f.category().equals("PLAN")?1:2)
                .thenComparing(Comparator.comparingInt((Fragment f)->historyRelevance(f.content(),terms)).reversed()));
        int budget=Math.max(256,Math.min(3500,requestedBudget));
        int remaining=budget;
        List<Fragment> included=new ArrayList<>(); StringBuilder content=new StringBuilder();
        content.append("H2：以下类型不能互换；PLAN 是未发生的作者计划；发言、信念不证明客观事实。没有知情依据不代表已知，也不证明一定不知。人物可用表述不转授给其他人物。未提供称谓、性别或身份时使用姓名，不自行补全。观察到结果不代表知道其原因。\n");
        content.append("认知来源约束：BELIEF/BELIEVED 只支持人物相信，REPORTED 只支持听说，INFERRED 只支持推断。不得为这些内容补写亲眼目睹、亲手实施、可靠证人、事后核实或具体回忆来把它们坐实；获知过程未给出时保持未给出。梦境只属于梦境。即使需要补足篇幅，也只扩展本场允许的动作、感官与选择，不补造关键往事。限知叙述仅描写视角人物内心；对他人意图用可观察动作或明确不确定的猜测，不写成确定知晓。\n");
        content.append("创作边界：可自由补充本场普通动作、环境、感官和对白措辞。关键事件的实施者、获知来源、重要往事、身份与核心设定只采用作者批准内容，不通过回忆、比喻或内心独白补造。knowledge.character 是相信或获知的人，不是事件实施者。eventActor 和 acquisitionBasis 只在原 content/kind/certainty 限定内适用；梦中行动者不迁移到现实，信念有来源也不证明为真。NOT_PROVIDED 表示作者未提供依据，不能猜测补齐，也不表示人物一定不知道。若 content 已明确行动者或来源可按原义使用；未指定行动者则保持未指定，不默认成自己。不要把这些标记写进小说。\n");
        remaining-=estimateTokens(content.toString());
        if(remaining<0) throw invalid("H2_REQUIRED_CONTEXT_TOO_LARGE");
        for (Fragment f:eligible) {
            String formatted="["+f.category()+"] "+f.content()+"\n";
            if (estimateTokens(formatted)>remaining) {
                if (f.category().equals("CONTRACT") || f.category().equals("PLAN")) throw invalid("H2_REQUIRED_CONTEXT_TOO_LARGE");
                excluded.add(new Fragment(f.id(),f.category(),f.content(),"预算不足，整条排除",true)); continue;
            }
            content.append(formatted); remaining-=estimateTokens(formatted); included.add(f);
        }
        Stamp stamp=new Stamp(m.getId(),branchId,m.getVersion(),ledger.canonRevision(),state.revision(),state.settingsRevision(),hash(m.getOutline().getContentJson()));
        return new Preview(view,characterId,sceneId,stamp,hash(VERSION+write(stamp)+view+content),budget,
                estimateTokens(content.toString()),content.toString(),List.copyOf(included),List.copyOf(excluded));
    }

    private List<Map<String,Object>> knowledge(NarrativeDtos.RecordView r, Document doc, Map<UUID,NarrativeDtos.Position> positions, int cutoff, Map<UUID,String> names, UUID onlyCharacter) {
        List<Map<String,Object>> result=new ArrayList<>();
        r.assertion().knowledge().stream().filter(k->k.view()!=null && (onlyCharacter==null || k.characterId().equals(onlyCharacter))).forEach(k ->
                result.add(knowledgeProjection(names.getOrDefault(k.characterId(),"未知人物"),k.view())));
        doc.grants().stream().filter(g -> !g.stale() && g.recordId().equals(r.id()) && positions.containsKey(g.fromSceneId()) && positions.get(g.fromSceneId()).index()<=cutoff)
                .filter(g->g.view()!=null && (onlyCharacter==null || g.characterId().equals(onlyCharacter))).forEach(g->
                        result.add(knowledgeProjection(names.getOrDefault(g.characterId(),"未知人物"),g.view())));
        return result;
    }

    private Map<String,Object> knowledgeProjection(String character,NarrativeDtos.KnowledgeView view) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("character",character);result.put("content",view.content());
        result.put("kind",view.kind());result.put("certainty",view.certainty());
        result.put("eventActor",view.eventActor()==null?"NOT_PROVIDED":view.eventActor());
        result.put("acquisitionBasis",view.acquisitionBasis()==null?"NOT_PROVIDED":view.acquisitionBasis());
        return result;
    }

    @Transactional
    public void requireStamp(User user, Stamp stamp) {
        var state=state(user,stamp.manuscriptId(),stamp.branchId());
        var m=access.requireOwnedManuscript(stamp.manuscriptId(),user);
        if (!stamp.branchId().equals(m.getCurrentBranchId()) || state.manuscriptVersion()!=stamp.manuscriptVersion()
                || state.canonRevision()!=stamp.canonRevision() || state.revision()!=stamp.contextRevision()
                || state.settingsRevision()!=stamp.settingsRevision() || !hash(m.getOutline().getContentJson()).equals(stamp.orderHash()))
            throw conflict("H2_CONTEXT_CHANGED");
    }

    private NarrativeContextRevision append(UUID branch,long revision,Document doc,String key,String requestHash,UUID author) {
        var row=new NarrativeContextRevision(); row.setBranchId(branch); row.setRevision(revision); row.setDocumentJson(write(doc));
        row.setIdempotencyKey(key); row.setRequestHash(requestHash); row.setCreatedAt(Instant.now()); row.setAuthorId(author);
        return revisions.saveAndFlush(row);
    }
    private com.fasterxml.jackson.databind.JsonNode tree(String s) { try { return json.readTree(s==null?"{}":s); } catch(Exception e) { throw new IllegalStateException("H2_STORED_DATA_INVALID",e); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String s,Class<T> type) { try { return json.readValue(s,type); } catch(Exception e) { throw new IllegalStateException("H2_STORED_DATA_INVALID",e); } }
    private static ApiStatusException conflict(String code) { return new ApiStatusException(HttpStatus.CONFLICT,code); }
}
