package com.ainovel.app.quality.language;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.*;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2VersionPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ainovel.app.quality.language.LanguageDtos.*;

class LanguageQualityServiceTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final ManuscriptRepository manuscripts=mock(ManuscriptRepository.class);
    final ManuscriptContentService contents=mock(ManuscriptContentService.class);
    final LanguageReportRepository reports=mock(LanguageReportRepository.class);
    final LanguagePatchRepository patches=mock(LanguagePatchRepository.class);
    final LanguageDecisionRepository decisions=mock(LanguageDecisionRepository.class);
    final LanguageFeature flags=mock(LanguageFeature.class);
    final AiOperationService operations=mock(AiOperationService.class);
    final V2VersionPersistenceService versions=mock(V2VersionPersistenceService.class);
    final Map<UUID,LanguageReport> savedReports=new LinkedHashMap<>(); final Map<UUID,LanguagePatch> savedPatches=new LinkedHashMap<>();
    final Map<String,LanguageDecision> receipts=new HashMap<>();
    final AtomicReference<String> html=new AtomicReference<>("<p>找这个动作还在。</p><p>她放下书。</p><p>屋外有人说话。</p><p>雨停了。</p><p>抽出来，是票。</p>");
    final User user=new User(); final Manuscript m=new Manuscript(); final UUID scene=UUID.randomUUID();
    final LanguageQualityService service;
    LanguageQualityServiceTest() {
        user.setId(UUID.randomUUID()); var story=new Story(); story.setId(UUID.randomUUID());story.setUser(user);story.setSynopsis("H2隐藏的未来秘密");
        var outline=new Outline(); outline.setStory(story); m.setId(UUID.randomUUID());m.setOutline(outline);m.setVersion(1);m.setCurrentBranchId(UUID.randomUUID());
        when(manuscripts.findByIdForUpdate(m.getId())).thenReturn(Optional.of(m));when(manuscripts.findWithStoryById(m.getId())).thenReturn(Optional.of(m));
        when(contents.readScene(m,scene)).thenAnswer(i->html.get());doAnswer(i->{html.set(i.getArgument(2));return null;}).when(contents).writeScene(eq(m),eq(scene),anyString());
        when(manuscripts.saveAndFlush(m)).thenAnswer(i->{m.setVersion(m.getVersion()+1);return m;});
        when(versions.createVersion(eq(m),eq(user),anyMap())).thenAnswer(i->Map.of("id",UUID.randomUUID()));
        when(versions.createLanguageVersion(eq(m),eq(user),anyBoolean())).thenAnswer(i->Map.of("id",UUID.randomUUID()));
        when(flags.selection(any())).thenAnswer(i->new LanguageStandard.Selection(LanguageStandard.VERSION,flags.generation(i.getArgument(0)),flags.checkAfterGeneration(i.getArgument(0))));
        when(flags.diagnosisEnabled()).thenReturn(true);when(flags.checkAfterGeneration(any())).thenReturn(true);
        when(reports.findById(any())).thenAnswer(i->Optional.ofNullable(savedReports.get(i.getArgument(0))));
        when(reports.findByManuscriptIdAndSceneIdAndSourceKey(any(),any(),any())).thenAnswer(i->savedReports.values().stream().filter(r->r.sourceKey.equals(i.getArgument(2))).findFirst());
        when(reports.saveAndFlush(any())).thenAnswer(i->{LanguageReport r=i.getArgument(0);if(r.id==null)r.id=UUID.randomUUID();savedReports.put(r.id,r);return r;});
        when(reports.save(any())).thenAnswer(i->{LanguageReport r=i.getArgument(0);savedReports.put(r.id,r);return r;});
        when(patches.findById(any())).thenAnswer(i->Optional.ofNullable(savedPatches.get(i.getArgument(0))));
        when(patches.findByReportIdAndIssueId(any(),any())).thenAnswer(i->savedPatches.values().stream().filter(p->p.reportId.equals(i.getArgument(0))&&p.issueId.equals(i.getArgument(1))).findFirst());
        when(patches.findByReportIdOrderByCreatedAtAsc(any())).thenAnswer(i->savedPatches.values().stream().filter(p->p.reportId.equals(i.getArgument(0))).toList());
        when(patches.saveAndFlush(any())).thenAnswer(i->{LanguagePatch p=i.getArgument(0);p.id=UUID.randomUUID();savedPatches.put(p.id,p);return p;});
        when(patches.save(any())).thenAnswer(i->{LanguagePatch p=i.getArgument(0);savedPatches.put(p.id,p);return p;});
        when(decisions.findByPatchIdAndRequestKey(any(),any())).thenAnswer(i->Optional.ofNullable(receipts.get(i.getArgument(0)+":"+i.getArgument(1))));
        when(decisions.save(any())).thenAnswer(i->{LanguageDecision d=i.getArgument(0);receipts.put(d.patchId+":"+d.requestKey,d);return d;});
        when(operations.submit(any(),anyString(),anyString(),any(),any(),anyInt(),anyString(),anyString())).thenAnswer(i->new AiOperationDtos.Accepted(UUID.randomUUID()));
        when(operations.get(any(),any())).thenAnswer(i->new AiOperationDtos.Progress(i.getArgument(1),"LANGUAGE_DIAGNOSIS","LANGUAGE_REPORT",null,AiOperationStatus.SUCCEEDED,"完成",1,1,0,0,false,1,null,null,null,null,null));
        service=new LanguageQualityService(manuscripts,contents,mock(ResourceAccessGuard.class),versions,reports,patches,decisions,flags,operations,json,mock(ApplicationEventPublisher.class),20000,100000,24000);
    }
    UUID report() {
        service.start(user,m.getId(),scene,new CheckRequest(m.getCurrentBranchId(),m.getVersion())); return savedReports.keySet().iterator().next();
    }
    void diagnose(UUID id) throws Exception {
        var input=service.input(user,id); var p=input.data().projection;
        var items=List.of(Map.of("kind","LANGUAGE","category","AWKWARD","quote","找这个动作还在。","impact","抽象化生硬","direction","表达人物的想法"),
                Map.of("kind","LANGUAGE","category","CHOPPY","quote","抽出来，是票。","impact","动作与发现硬切","direction","自然承接"));
        var parsed=LanguageAnalysis.parse(json.writeValueAsString(Map.of("complete",true,"issues",items)),p,input.data().coverage.getFirst(),json);
        service.checked(user,id,0,parsed,null);service.finish(user,id);
    }
    LanguagePatch candidate(UUID report,String issue,String replacement,String meaning) throws Exception {
        service.suggest(user,m.getId(),report,issue); var p=savedPatches.values().stream().filter(v->v.issueId.equals(issue)).findFirst().orElseThrow();
        service.candidate(user,p.id,json.writeValueAsString(Map.of("outcome","CANDIDATE","replacement",replacement)));
        service.reviewed(user,p.id,json.writeValueAsString(Map.of("language","PASS","languageReason","自然","meaning",meaning,"meaningReason","复核说明","changes",List.of())),null);
        return p;
    }
    DecisionResult decide(UUID report,LanguagePatch p,String action,String key) { return service.decide(user,m.getId(),report,p.id,action,new DecisionRequest(m.getCurrentBranchId(),m.getVersion()),key); }
    @Test void zeroRuleHitsStillRegisterAndSameVersionIsDeduplicatedWithoutCreatingRewrites() {
        html.set("<p>她回来了。</p>");var id=report();var source=service.input(user,id).data();
        service.start(user,m.getId(),scene,new CheckRequest(m.getCurrentBranchId(),m.getVersion()));
        assertEquals(1,savedReports.size()); assertTrue(savedPatches.isEmpty()); assertEquals("她回来了。",source.projection.text());
        assertFalse(source.projection.text().contains("H2隐藏")); verify(operations,times(1)).submit(any(),eq("LANGUAGE_DIAGNOSIS"),anyString(),any(),any(),anyInt(),anyString(),anyString());
    }
    @Test void generationWithBackgroundCheckOffShowsLocalOnlyAndManualCheckUpgradesTheSameSource() {
        when(flags.checkAfterGeneration(any())).thenReturn(false);when(flags.generation(any())).thenReturn(true);
        service.afterGeneration(user,m,scene,UUID.randomUUID());var id=savedReports.keySet().iterator().next();
        assertEquals(Status.LOCAL_ONLY,service.input(user,id).data().status);verifyNoInteractions(operations);
        service.start(user,m.getId(),scene,new CheckRequest(m.getCurrentBranchId(),m.getVersion()));
        assertEquals(Status.CHECKING,service.input(user,id).data().status);
        verify(operations,times(1)).submit(any(),eq("LANGUAGE_DIAGNOSIS"),anyString(),any(),any(),anyInt(),anyString(),anyString());
    }
    @Test void needsContextPersistsReasonDeduplicatesAndCannotBeAccepted() throws Exception {
        UUID report=report();diagnose(report);var accepted=service.suggest(user,m.getId(),report,"0-0");
        var p=savedPatches.values().iterator().next();
        service.candidate(user,p.id,"{\"outcome\":\"NEEDS_CONTEXT\",\"replacement\":null,\"reason\":\"无法确定行动者\"}");
        var dto=service.patch(user,m.getId(),report,p.id);
        assertEquals("NEEDS_CONTEXT",dto.status());assertEquals("无法确定行动者",dto.reason());assertNull(dto.replacement());assertNull(dto.review());
        assertEquals(accepted,service.suggest(user,m.getId(),report,"0-0"));assertThrows(ApiStatusException.class,()->decide(report,p,"accept","cannot-accept"));
        assertEquals("REJECTED",decide(report,p,"reject","reject-no-candidate").patch().status());
    }
    @Test void historicalParserPreservesOldCandidatesButChangedProfileBlocksInference() throws Exception {
        UUID report=report();diagnose(report);service.suggest(user,m.getId(),report,"0-0");var p=savedPatches.values().iterator().next();
        var stored=savedReports.get(report);var d=json.readValue(stored.dataJson,ReportData.class);var src=d.source;
        d.source=new Source(src.branchId(),src.bodyVersion(),src.snapshotId(),src.projectionVersion(),src.htmlHash(),src.textHash(),src.contextVersion(),"zh-naturalness-v2");d.profileHash=null;stored.dataJson=json.writeValueAsString(d);
        service.candidate(user,p.id,"{\"replacement\":\"她还是想找。\"}");assertEquals("她还是想找。",service.patchInput(user,p.id).data().replacement);
        d.profileHash="changed";stored.dataJson=json.writeValueAsString(d);
        assertThrows(com.ainovel.app.ai.AiResultUncertainException.class,()->service.patchInput(user,p.id));
    }
    @Test void acceptsRebasesIndependentPatchAndUndoCreatesNewVersion() throws Exception {
        UUID id=report();diagnose(id); var first=candidate(id,"0-0","她还是想去找寄信人。","PASS");var last=candidate(id,"0-1","她拿出来一看，原来是票。","PASS");
        String original=html.get();var accepted=decide(id,first,"accept","accept-first");assertEquals(2,accepted.bodyVersion());
        assertEquals("STALE",service.get(user,m.getId(),id).status().name());assertTrue(service.get(user,m.getId(),id).canContinueBatch());
        assertEquals("APPLICABLE",service.patch(user,m.getId(),id,last.id).applicability());decide(id,last,"accept","accept-last");
        assertTrue(html.get().contains("她拿出来一看，原来是票。"));decide(id,last,"undo","undo-last");decide(id,first,"undo","undo-first");
        assertEquals(original,html.get());assertEquals(5,m.getVersion());assertEquals(4,receipts.size());
    }
    @Test void sameRequestReusesCandidateAndDecisionAndRejectsReusedKeyWithDifferentAction() throws Exception {
        UUID id=report();diagnose(id);var p=candidate(id,"0-0","她还是想去找寄信人。","PASS");
        service.suggest(user,m.getId(),id,p.issueId);assertEquals(1,savedPatches.size());
        var request=new DecisionRequest(m.getCurrentBranchId(),m.getVersion());var first=service.decide(user,m.getId(),id,p.id,"accept",request,"same-request");
        assertEquals(first,service.decide(user,m.getId(),id,p.id,"accept",request,"same-request"));assertEquals(2,m.getVersion());
        assertThrows(ApiStatusException.class,()->service.decide(user,m.getId(),id,p.id,"undo",request,"same-request"));
    }
    @Test void changedMeaningCannotBeAppliedAndRejectDoesNotWriteBody() throws Exception {
        UUID id=report();diagnose(id);var p=candidate(id,"0-0","她决定今天去找人。","FAIL");
        assertThrows(ApiStatusException.class,()->decide(id,p,"accept","blocked-meaning"));decide(id,p,"reject","reject-meaning");
        verify(contents,never()).writeScene(any(),any(),anyString());assertEquals(1,m.getVersion());
    }
    @Test void concurrentEditBranchAndOwnershipPreventOverwrite() throws Exception {
        UUID id=report();diagnose(id);var p=candidate(id,"0-0","她还是想去找寄信人。","PASS");
        m.setVersion(2);assertThrows(ApiStatusException.class,()->decide(id,p,"accept","edited-version"));m.setVersion(1);
        m.setCurrentBranchId(UUID.randomUUID());assertThrows(ApiStatusException.class,()->decide(id,p,"accept","other-branch"));
        var other=new User();other.setId(UUID.randomUUID());assertThrows(ApiStatusException.class,()->service.get(other,m.getId(),id));
        assertThrows(ApiStatusException.class,()->service.patch(user,UUID.randomUUID(),id,p.id)); verify(contents,never()).writeScene(any(),any(),anyString());
    }
    @Test void sameParagraphSuggestionsAreInvalidatedAndOrdinaryEditBlocksUndo() throws Exception {
        UUID id=report();diagnose(id);var p=candidate(id,"0-0","她还是想去找寄信人。","UNCERTAIN");decide(id,p,"accept","accept-uncertain");
        assertEquals("STALE",service.get(user,m.getId(),id).issues().getFirst().availability());
        html.set(html.get()+"<p>作者新写的结尾。</p>");m.setVersion(m.getVersion()+1);
        assertThrows(ApiStatusException.class,()->decide(id,p,"undo","refuse-undo"));assertTrue(html.get().endsWith("<p>作者新写的结尾。</p>"));
    }
}
