package com.ainovel.app.quality.language;

import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.ManuscriptContentService;
import com.ainovel.app.manuscript.attribution.SceneContentEditedEvent;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.ainovel.app.quality.language.LanguageDtos.*;

@Service
public class LanguageQualityService {
    private final ManuscriptRepository manuscripts;
    private final ManuscriptContentService contents;
    private final ResourceAccessGuard access;
    private final V2VersionPersistenceService versions;
    private final LanguageReportRepository reports;
    private final LanguagePatchRepository patches;
    private final LanguageDecisionRepository decisions;
    private final LanguageFeature feature;
    private final AiOperationService operations;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final int maxMessage, maxTotal, inputTokens;
    public LanguageQualityService(ManuscriptRepository manuscripts, ManuscriptContentService contents, ResourceAccessGuard access,
            V2VersionPersistenceService versions, LanguageReportRepository reports, LanguagePatchRepository patches,
            LanguageDecisionRepository decisions, LanguageFeature feature, @Lazy AiOperationService operations,
            ObjectMapper json, ApplicationEventPublisher events,
            @Value("${app.ai.admission.max-message-chars:20000}") int maxMessage,
            @Value("${app.ai.admission.max-total-chars:100000}") int maxTotal,
            @Value("${app.language.input-token-budget:24000}") int inputTokens) {
        this.manuscripts=manuscripts; this.contents=contents; this.access=access; this.versions=versions; this.reports=reports;
        this.patches=patches; this.decisions=decisions; this.feature=feature; this.operations=operations; this.json=json; this.events=events;
        this.maxMessage=maxMessage; this.maxTotal=maxTotal; this.inputTokens=inputTokens;
    }
    @Transactional(readOnly=true)
    public Settings settings(User user,UUID manuscriptId) { return feature.settings(owned(user,manuscriptId,false).getOutline().getStory().getId()); }
    @Transactional
    public Settings settings(User user,UUID manuscriptId,SettingsRequest input) {
        return feature.save(owned(user,manuscriptId,true).getOutline().getStory().getId(),input);
    }
    @Transactional
    public AiOperationDtos.Accepted start(User user,UUID manuscriptId,UUID sceneId,CheckRequest expected) {
        requireEnabled(); Manuscript m=owned(user,manuscriptId,true); requireExpected(m,expected.expectedBranchId(),expected.expectedVersion());
        versions.ensureGenerationBaseline(m,user); manuscripts.flush();
        return register(user,m,sceneId,null,true,LanguageStandard.VERSION);
    }
    /** Called inside the generation save transaction. AiOperation dispatch waits for its commit. */
    @Transactional
    public void afterGeneration(User user,Manuscript m,UUID sceneId,UUID snapshotId) {
        UUID storyId=m.getOutline().getStory().getId();
        afterGeneration(user,m,sceneId,snapshotId,feature.selection(storyId));
    }
    @Transactional
    public void afterGeneration(User user,Manuscript m,UUID sceneId,UUID snapshotId,LanguageStandard.Selection selection) {
        if(selection.diagnosis()) register(user,m,sceneId,snapshotId,true,selection.version());
        else if(selection.generation()) register(user,m,sceneId,snapshotId,false,selection.version());
    }
    private AiOperationDtos.Accepted register(User user,Manuscript m,UUID sceneId,UUID snapshotId,boolean diagnose,String version) {
        var projection=LanguageProjection.of(contents.readScene(m,sceneId));
        if(projection.text().isBlank()) throw invalid("LANGUAGE_EMPTY_SCENE");
        String key=LanguageProjection.hash(m.getCurrentBranchId()+":"+m.getVersion()+":"+version+":"+projection.htmlHash());
        var prior=reports.findByManuscriptIdAndSceneIdAndSourceKey(m.getId(),sceneId,key);
        if(prior.isPresent() && data(prior.get()).operationId!=null) return new AiOperationDtos.Accepted(data(prior.get()).operationId);
        if(prior.isPresent() && !diagnose) return null;
        if(snapshotId==null && prior.isEmpty()) snapshotId=snapshot(m,user,"language_check");
        ReportData d=prior.isPresent()?data(prior.get()):new ReportData(); d.projection=projection;
        // Language checks need only the saved scene. No character cards, future plans, private world notes or retrieval are sent.
        if(prior.isEmpty()) d.source=new Source(m.getCurrentBranchId(),m.getVersion(),snapshotId,projection.version(),projection.htmlHash(),projection.textHash(),
                LanguageProjection.hash("scene-only:"+projection.textHash()),version);
        d.profileHash=LanguageStandard.hash(version);
        d.applicableVersion=m.getVersion(); d.applicableHtmlHash=projection.htmlHash();
        d.coverage=LanguageAnalysis.windows(projection,json,maxMessage,maxTotal,inputTokens,version);
        d.status=diagnose?Status.CHECKING:Status.LOCAL_ONLY;
        d.summary=diagnose?"等待语言检查":"生成时仅执行了本地规则，尚未进行语言自然度诊断。";
        if(!diagnose) d.coverage=d.coverage.stream().map(c->new Coverage(c.index(),c.start(),c.end(),c.contextStart(),c.contextEnd(),"SKIPPED","未启用生成后语言检查")).toList();
        LanguageReport report=prior.orElseGet(LanguageReport::new); report.manuscriptId=m.getId(); report.sceneId=sceneId; report.sourceKey=key; report.setDataJson(write(d));
        reports.saveAndFlush(report);
        if(!diagnose) return null;
        var accepted=operations.submit(user,"LANGUAGE_DIAGNOSIS","LANGUAGE_REPORT",report.id,Map.of("reportId",report.id),
                Math.max(1,d.coverage.size()),"等待后台语言检查","language-check:"+report.id);
        d.operationId=accepted.operationId(); save(report,d); return accepted;
    }
    @Transactional(readOnly=true)
    public List<ReportDto> list(User user,UUID manuscriptId,UUID sceneId) {
        var m=owned(user,manuscriptId,false);
        return reports.findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(manuscriptId,sceneId).stream().map(r->dto(user,m,r)).toList();
    }
    @Transactional(readOnly=true)
    public ReportDto get(User user,UUID manuscriptId,UUID reportId) {
        var m=owned(user,manuscriptId,false); return dto(user,m,report(manuscriptId,reportId));
    }
    public record CheckInput(UUID manuscriptId,UUID reportId,ReportData data) {}
    @Transactional(readOnly=true)
    public CheckInput input(User user,UUID reportId) {
        var r=reports.findById(reportId).orElseThrow(()->missing()); owned(user,r.manuscriptId,false);
        var d=data(r); requireProfile(d); return new CheckInput(r.manuscriptId,r.id,d);
    }
    @Transactional
    public void checked(User user,UUID reportId,int index,LanguageAnalysis.Parsed parsed,String failure) {
        var r=reports.findById(reportId).orElseThrow(()->missing()); owned(user,r.manuscriptId,true); var d=data(r);
        var c=d.coverage.get(index); if(!c.state().equals("PENDING")) return;
        String state=failure!=null?"FAILED":parsed.complete()?"COMPLETE":"PARTIAL";
        d.coverage.set(index,new Coverage(c.index(),c.start(),c.end(),c.contextStart(),c.contextEnd(),state,
                failure!=null?failure:parsed.summary()));
        if(parsed!=null) d.issues.addAll(parsed.issues());
        if(failure!=null) {
            for(int i=index+1;i<d.coverage.size();i++) {
                var next=d.coverage.get(i); if(next.state().equals("PENDING")) d.coverage.set(i,new Coverage(next.index(),next.start(),next.end(),next.contextStart(),next.contextEnd(),"SKIPPED","前一检查未完成，未继续付费调用"));
            }
        }
        save(r,d);
    }
    @Transactional
    public Map<String,UUID> finish(User user,UUID reportId) {
        var r=reports.findById(reportId).orElseThrow(()->missing()); owned(user,r.manuscriptId,true); var d=data(r);
        boolean all=d.coverage.stream().allMatch(c->c.state().equals("COMPLETE"));
        boolean any=d.coverage.stream().anyMatch(c->Set.of("COMPLETE","PARTIAL").contains(c.state()));
        boolean failed=d.coverage.stream().anyMatch(c->c.state().equals("FAILED"));
        d.status=!all?(any || !failed?Status.INCOMPLETE:Status.FAILED):d.issues.isEmpty()?Status.NO_CLEAR_ISSUES:Status.ISSUES;
        d.summary=all?(d.issues.isEmpty()?"已扫描记录范围，未发现明确语言问题；这不是文本完美或没有 AI 味的保证。":"语言检查已完成，请逐项审阅；文风建议与观察不等于明确错误。")
                :"语言检查未覆盖全部正文或结果不可用；未检查部分不能视为通过。";
        save(r,d); return Map.of("reportId",r.id);
    }
    @Transactional
    public AiOperationDtos.Accepted suggest(User user,UUID manuscriptId,UUID reportId,String issueId) {
        requireEnabled(); var m=owned(user,manuscriptId,true); var r=report(manuscriptId,reportId); var d=data(r);
        var existing=patches.findByReportIdAndIssueId(reportId,issueId);
        if(existing.isPresent()) return new AiOperationDtos.Accepted(patchData(existing.get()).operationId);
        if(d.status==Status.CHECKING) throw conflict("LANGUAGE_CHECK_IN_PROGRESS");
        if(!matches(m,r,d)) throw conflict("LANGUAGE_REPORT_STALE");
        var issue=d.issues.stream().filter(i->i.id().equals(issueId)).findFirst().orElseThrow(()->missing());
        if(!issue.availability().equals("AVAILABLE") || !issue.location().equals("EXACT")) throw conflict("LANGUAGE_LOCATION_UNTRUSTED");
        var p=LanguageProjection.of(contents.readScene(m,r.sceneId)); var paragraph=p.paragraphAt(issue.currentStart(),issue.currentEnd());
        if(paragraph==null) throw conflict("LANGUAGE_LOCATION_UNTRUSTED");
        var range=LanguageAnalysis.revisionRange(p,issue);
        var pd=new PatchData(); pd.start=range[0]; pd.end=range[1]; pd.paragraph=paragraph.index();
        pd.original=p.text().substring(pd.start,pd.end); pd.beforeContext=localContext(p,paragraph.index()); pd.applicableVersion=m.getVersion(); pd.applicableHtmlHash=p.htmlHash();
        pd.contextOffset=pd.start-p.paragraphs().get(Math.max(0,paragraph.index()-1)).start();
        pd.applicability=p.canReplace(pd.start,pd.end)?"PENDING":"MANUAL_ONLY";
        var patch=new LanguagePatch(); patch.reportId=r.id; patch.issueId=issue.id(); patch.dataJson=write(pd); patches.saveAndFlush(patch);
        var operation=operations.submit(user,"LANGUAGE_SUGGESTION","LANGUAGE_PATCH",patch.id,Map.of("patchId",patch.id),2,"生成局部候选","language-patch:"+patch.id);
        pd.operationId=operation.operationId(); save(patch,pd); return operation;
    }
    public record PatchInput(UUID manuscriptId,UUID reportId,UUID patchId,Issue issue,PatchData data,String standardVersion) {}
    @Transactional(readOnly=true)
    public PatchInput patchInput(User user,UUID patchId) {
        var p=patches.findById(patchId).orElseThrow(()->missing()); var r=reports.findById(p.reportId).orElseThrow(()->missing());
        owned(user,r.manuscriptId,false); var d=data(r);
        var issue=d.issues.stream().filter(i->i.id().equals(p.issueId)).findFirst().orElseThrow(()->missing());
        requireProfile(d); return new PatchInput(r.manuscriptId,r.id,p.id,issue,patchData(p),d.source.standardVersion());
    }
    @Transactional
    public void candidate(User user,UUID patchId,String raw) {
        var p=patches.findById(patchId).orElseThrow(()->missing()); var r=reports.findById(p.reportId).orElseThrow(()->missing());
        owned(user,r.manuscriptId,true); var d=patchData(p); if(d.candidateRaw!=null || !d.status.equals("GENERATING")) return;
        d.candidateRaw=raw;
        try {
            var node=LanguageAnalysis.json(raw,json);
            if(LanguageStandard.VERSION.equals(data(r).source.standardVersion())) {
                String outcome=LanguageAnalysis.required(node,"outcome");
                if(outcome.equals("NEEDS_CONTEXT")) {
                    if(node.hasNonNull("replacement")) throw new IllegalArgumentException();
                    d.reason=LanguageAnalysis.required(node,"reason");
                    d.status="NEEDS_CONTEXT"; d.applicability="NEEDS_CONTEXT"; save(p,d); return;
                }
                if(!outcome.equals("CANDIDATE")) throw new IllegalArgumentException();
            }
            d.replacement=LanguageAnalysis.required(node,"replacement");
            if(d.replacement.contains("\n") || d.replacement.contains("\r") || d.replacement.contains("<") || d.replacement.equals(d.original)) throw new IllegalArgumentException();
            d.afterContext=d.beforeContext.substring(0,d.contextOffset)+d.replacement+d.beforeContext.substring(d.contextOffset+d.original.length());
        } catch(IllegalArgumentException e) { d.status="FAILED"; d.applicability="INVALID_CANDIDATE"; }
        save(p,d);
    }
    @Transactional
    public Map<String,UUID> reviewed(User user,UUID patchId,String raw,String error) {
        var p=patches.findById(patchId).orElseThrow(()->missing()); var r=reports.findById(p.reportId).orElseThrow(()->missing());
        owned(user,r.manuscriptId,true); var d=patchData(p); if(d.reviewRaw!=null || !d.status.equals("GENERATING")) return Map.of("patchId",p.id);
        d.reviewRaw=raw;
        try {
            if(error!=null) throw new IllegalArgumentException();
            d.review=LanguageAnalysis.review(raw,json); d.status="READY";
            if(d.review.meaning()==Verdict.FAIL || d.review.language()==Verdict.FAIL) d.applicability="BLOCKED";
            else if(!d.applicability.equals("MANUAL_ONLY")) d.applicability=d.review.meaning()==Verdict.UNCERTAIN || d.review.language()==Verdict.UNCERTAIN?"UNCERTAIN":"APPLICABLE";
        } catch(IllegalArgumentException e) { d.status="FAILED"; d.applicability=error==null?"INVALID_REVIEW":error; }
        save(p,d); return Map.of("patchId",p.id);
    }
    @Transactional(readOnly=true)
    public PatchDto patch(User user,UUID manuscriptId,UUID reportId,UUID patchId) {
        var m=owned(user,manuscriptId,false); var r=report(manuscriptId,reportId); return patchDto(m,r,requirePatch(reportId,patchId));
    }
    @Transactional
    public DecisionResult decide(User user,UUID manuscriptId,UUID reportId,UUID patchId,String action,DecisionRequest request,String requestKey) {
        if(requestKey==null || !requestKey.matches("[A-Za-z0-9:_-]{8,80}")) throw invalid("INVALID_IDEMPOTENCY_KEY");
        var m=owned(user,manuscriptId,true); var r=report(manuscriptId,reportId); var p=requirePatch(reportId,patchId);
        String requestHash=LanguageProjection.hash(action+":"+write(request));
        var prior=decisions.findByPatchIdAndRequestKey(patchId,requestKey);
        if(prior.isPresent()) {
            if(!prior.get().requestHash.equals(requestHash)) throw conflict("LANGUAGE_DECISION_KEY_CONFLICT");
            return read(prior.get().resultJson,DecisionResult.class);
        }
        requireExpected(m,request.expectedBranchId(),request.expectedVersion());
        var rd=data(r); var pd=patchData(p);
        if(!Objects.equals(rd.source.branchId(),m.getCurrentBranchId())) throw conflict("LANGUAGE_BRANCH_CHANGED");
        String html=contents.readScene(m,r.sceneId);
        if(action.equals("reject")) {
            if(pd.status.equals("GENERATING") && pd.operationId!=null && Set.of(AiOperationStatus.FAILED,AiOperationStatus.CANCELLED,AiOperationStatus.RECOVERY_REQUIRED).contains(operations.get(user,pd.operationId).status())) pd.status="FAILED";
            if(!Set.of("READY","FAILED","NEEDS_CONTEXT").contains(pd.status)) throw conflict("LANGUAGE_PATCH_DECIDED");
            pd.status="REJECTED";
        } else {
            boolean undo=action.equals("undo");
            if(!undo && !action.equals("accept")) throw invalid("LANGUAGE_DECISION_INVALID");
            if(undo?!pd.status.equals("ACCEPTED"):!pd.status.equals("READY")) throw conflict("LANGUAGE_PATCH_DECIDED");
            if(!undo && !Set.of("APPLICABLE","UNCERTAIN").contains(pd.applicability)) throw conflict("LANGUAGE_PATCH_BLOCKED");
            if(pd.applicableVersion!=m.getVersion() || !LanguageProjection.hash(html).equals(pd.applicableHtmlHash)) throw conflict("LANGUAGE_PATCH_STALE");
            var projection=LanguageProjection.of(html); String before=undo?pd.replacement:pd.original, after=undo?pd.original:pd.replacement;
            try { html=projection.replace(pd.start,pd.end,before,after); }
            catch(IllegalArgumentException e) { throw conflict("LANGUAGE_PATCH_LOCATION_CHANGED"); }
            contents.writeScene(m,r.sceneId,html); manuscripts.saveAndFlush(m);
            UUID snapshotId=snapshot(m,user,undo?"language_undo":"language_accept");
            if(undo) { pd.status="UNDONE"; pd.undoneSnapshotId=snapshotId; } else { pd.status="ACCEPTED"; pd.appliedSnapshotId=snapshotId; }
            int delta=after.length()-before.length();
            rebase(r,rd,p,pd,projection,delta,m.getVersion(),LanguageProjection.hash(html));
            pd.end=pd.start+after.length(); pd.applicableVersion=m.getVersion(); pd.applicableHtmlHash=LanguageProjection.hash(html);
            events.publishEvent(new SceneContentEditedEvent(m.getId(),r.sceneId,html));
            events.publishEvent(new com.ainovel.app.narrative.NarrativeSourceChanged(m.getId(),null));
        }
        save(p,pd); save(r,rd);
        var result=new DecisionResult(patchDto(m,r,p),m.getVersion(),m.getCurrentBranchId(),html);
        var receipt=new LanguageDecision(); receipt.patchId=p.id; receipt.action=action; receipt.requestKey=requestKey; receipt.requestHash=requestHash; receipt.resultJson=write(result); decisions.save(receipt);
        return result;
    }
    private void rebase(LanguageReport r,ReportData rd,LanguagePatch changed,PatchData patch,LanguageProjection.Projection before,int delta,long version,String hash) {
        int paragraph=before.paragraphAt(patch.start,patch.end).index();
        rd.issues=rd.issues.stream().map(i->{
            if(i.currentStart()<0) return i;
            var current=before.paragraphAt(i.currentStart(),i.currentEnd());
            boolean affected=current==null || Math.abs(current.index()-paragraph)<=1;
            int shift=i.currentStart()>=patch.end?delta:0;
            return new Issue(i.id(),i.kind(),i.category(),i.quote(),i.impact(),i.direction(),i.start(),i.end(),i.location(),i.paragraph(),i.context(),
                    i.currentStart()+shift,i.currentEnd()+shift,affected?"STALE":i.availability());
        }).toList();
        for(var other:patches.findByReportIdOrderByCreatedAtAsc(r.id)) {
            if(other.id.equals(changed.id)) continue; var d=patchData(other);
            var current=before.paragraphAt(d.start,d.end);
            if(current==null || Math.abs(current.index()-paragraph)<=1) d.applicability="STALE";
            else if(!d.applicability.equals("STALE")) {
                if(d.start>=patch.end) { d.start+=delta; d.end+=delta; }
                d.applicableVersion=version; d.applicableHtmlHash=hash;
            }
            save(other,d);
        }
        rd.applicableVersion=version; rd.applicableHtmlHash=hash;
    }
    private ReportDto dto(User user,Manuscript m,LanguageReport r) {
        var d=data(r); boolean current=LanguageStandard.VERSION.equals(d.source.standardVersion()) && m.getVersion()==d.source.bodyVersion() && Objects.equals(m.getCurrentBranchId(),d.source.branchId())
                && LanguageProjection.hash(contents.readScene(m,r.sceneId)).equals(d.source.htmlHash());
        Status status=current?d.status:Status.STALE; String summary=d.summary;
        if(current && status==Status.CHECKING && d.operationId!=null) {
            var op=operations.get(user,d.operationId);
            if(Set.of(AiOperationStatus.FAILED,AiOperationStatus.CANCELLED,AiOperationStatus.RECOVERY_REQUIRED).contains(op.status())) {
                status=d.issues.isEmpty()?Status.FAILED:Status.INCOMPLETE; summary="任务未完成："+op.status()+"。保留已有结果，未覆盖部分不视为通过。";
            }
        }
        return new ReportDto(r.id,r.manuscriptId,r.sceneId,d.source,status,summary,List.copyOf(d.issues),List.copyOf(d.coverage),
                patches.findByReportIdOrderByCreatedAtAsc(r.id).stream().map(p->patchDto(m,r,p)).toList(),d.operationId,matches(m,r,d),r.createdAt);
    }
    private PatchDto patchDto(Manuscript m,LanguageReport r,LanguagePatch p) {
        var d=patchData(p); var rd=data(r);
        String applicability=Objects.equals(m.getCurrentBranchId(),rd.source.branchId()) && m.getVersion()==d.applicableVersion
                && LanguageProjection.hash(contents.readScene(m,r.sceneId)).equals(d.applicableHtmlHash)?d.applicability:"STALE";
        String status=d.status;
        if(status.equals("GENERATING") && d.operationId!=null) {
            var op=operations.get(m.getOutline().getStory().getUser(),d.operationId);
            if(Set.of(AiOperationStatus.FAILED,AiOperationStatus.CANCELLED,AiOperationStatus.RECOVERY_REQUIRED).contains(op.status())) {
                status="FAILED"; if(!applicability.equals("STALE")) applicability=op.status().name();
            }
        }
        return new PatchDto(p.id,r.id,p.issueId,d.original,d.replacement,d.beforeContext,d.afterContext,d.review,status,applicability,d.operationId,d.appliedSnapshotId,d.undoneSnapshotId,p.createdAt,d.reason);
    }
    private void requireProfile(ReportData data) {
        try {
            String hash=LanguageStandard.hash(data.source.standardVersion());
            if(data.profileHash!=null && !data.profileHash.equals(hash)) throw new IllegalStateException("LANGUAGE_PROFILE_CHANGED");
            if(LanguageStandard.VERSION.equals(data.source.standardVersion()) && data.profileHash==null) throw new IllegalStateException("LANGUAGE_PROFILE_MISSING");
        } catch(RuntimeException unavailable) { throw new com.ainovel.app.ai.AiResultUncertainException(unavailable); }
    }
    private boolean matches(Manuscript m,LanguageReport r,ReportData d) {
        return d.status!=Status.CHECKING && LanguageStandard.VERSION.equals(d.source.standardVersion()) && Objects.equals(m.getCurrentBranchId(),d.source.branchId()) && m.getVersion()==d.applicableVersion
                && LanguageProjection.hash(contents.readScene(m,r.sceneId)).equals(d.applicableHtmlHash);
    }
    private Manuscript owned(User user,UUID id,boolean lock) {
        var m=(lock?manuscripts.findByIdForUpdate(id):manuscripts.findWithStoryById(id)).orElseThrow(()->missing());
        if(!m.getOutline().getStory().getUser().getId().equals(user.getId())) throw missing();
        return m;
    }
    private LanguageReport report(UUID manuscriptId,UUID id) { return reports.findById(id).filter(r->r.manuscriptId.equals(manuscriptId)).orElseThrow(()->missing()); }
    private LanguagePatch requirePatch(UUID reportId,UUID id) { return patches.findById(id).filter(p->p.reportId.equals(reportId)).orElseThrow(()->missing()); }
    private void requireExpected(Manuscript m,UUID branch,long version) {
        if(!Objects.equals(m.getCurrentBranchId(),branch) || m.getVersion()!=version) throw conflict("MANUSCRIPT_VERSION_CONFLICT");
    }
    private UUID snapshot(Manuscript m,User user,String type) {
        var saved=type.equals("language_accept") || type.equals("language_undo")
                ?versions.createLanguageVersion(m,user,type.equals("language_undo"))
                :versions.createVersion(m,user,Map.of("snapshotType",type,"label","语言检查 · "+type));
        return UUID.fromString(saved.get("id").toString());
    }
    private static String localContext(LanguageProjection.Projection p,int index) {
        var paragraphs=p.paragraphs(); return p.text().substring(paragraphs.get(Math.max(0,index-1)).start(),paragraphs.get(Math.min(paragraphs.size()-1,index+1)).end());
    }
    private void requireEnabled() { if(!feature.diagnosisEnabled()) throw conflict("LANGUAGE_FEATURE_DISABLED"); }
    private ReportData data(LanguageReport r) { return read(r.dataJson,ReportData.class); }
    private PatchData patchData(LanguagePatch p) { return read(p.dataJson,PatchData.class); }
    // Mutation must pass through the enhanced entity, otherwise Hibernate dirty tracking misses direct external field writes.
    private void save(LanguageReport r,ReportData d) { r.setDataJson(write(d)); reports.save(r); }
    private void save(LanguagePatch p,PatchData d) { p.setDataJson(write(d)); patches.save(p); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value,Class<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException("INVALID_LANGUAGE_RECORD",e); } }
    private static ApiStatusException missing() { return new ApiStatusException(HttpStatus.NOT_FOUND,"LANGUAGE_RECORD_NOT_FOUND"); }
    private static ApiStatusException conflict(String code) { return new ApiStatusException(HttpStatus.CONFLICT,code); }
    private static ApiStatusException invalid(String code) { return new ApiStatusException(HttpStatus.BAD_REQUEST,code); }
}
