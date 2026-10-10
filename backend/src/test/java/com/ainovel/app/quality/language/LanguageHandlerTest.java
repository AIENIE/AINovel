package com.ainovel.app.quality.language;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.ainovel.app.quality.language.LanguageDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LanguageHandlerTest {
    final LanguageQualityService quality=mock(LanguageQualityService.class);
    final AiService ai=mock(AiService.class);
    final EconomyService economy=mock(EconomyService.class);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final User user=new User(); final UUID id=UUID.randomUUID();
    final LanguageSuggestionHandler suggestion=new LanguageSuggestionHandler(quality,ai,economy,20000,100000,24000);
    AiOperationExecution execution(String key) throws Exception { return new AiOperationExecution(UUID.randomUUID(),user,json.writeValueAsString(Map.of(key,id)),json,(a,b,c)->{}); }
    AiChatResponse response(String content) { return new AiChatResponse("assistant",content,null,0); }
    PatchData patch(boolean candidateSaved) {
        var d=new PatchData(); d.original="找这个动作还在。";d.beforeContext=d.original;
        if(candidateSaved) {d.candidateRaw="saved";d.replacement="她还是想找。";}
        var issue=new Issue("0-0",Kind.LANGUAGE,Category.AWKWARD,d.original,"抽象生硬","恢复人物想法",0,8,"EXACT",0,d.original,0,8,"AVAILABLE");
        when(quality.patchInput(user,id)).thenReturn(new LanguageQualityService.PatchInput(UUID.randomUUID(),UUID.randomUUID(),id,issue,d,LanguageStandard.VERSION));
        return d;
    }
    @Test void oneCandidateAndOneReviewOnlyAndNoThirdCall() throws Exception {
        var d=patch(false);
        when(ai.chat(eq(user),any(),any())).thenReturn(response("candidate"),response("review"));
        doAnswer(i->{d.candidateRaw=i.getArgument(2);d.replacement="她还是想找。";return null;}).when(quality).candidate(eq(user),eq(id),anyString());
        suggestion.execute(execution("patchId"));
        verify(ai,times(2)).chat(eq(user),any(),any());verify(quality).reviewed(user,id,"review",null);
    }
    @Test void restartWithSavedCandidateOnlyReviewsWithoutGeneratingAgain() throws Exception {
        patch(true);when(ai.chat(eq(user),any(),any())).thenReturn(response("review"));
        suggestion.execute(execution("patchId"));
        verify(ai,times(1)).chat(eq(user),any(),eq(LanguageSuggestionHandler.usage(id,"review")));
        verify(quality,never()).candidate(any(),any(),any());
    }
    @Test void unknownResultRequiresRecoveryAndDoesNotContinueWithReview() throws Exception {
        patch(false);when(ai.chat(eq(user),any(),any())).thenThrow(new AiResultUncertainException());
        assertThrows(AiResultUncertainException.class,()->suggestion.execute(execution("patchId")));
        verify(ai,times(1)).chat(eq(user),any(),any());verify(quality,never()).reviewed(any(),any(),any(),any());
        suggestion.recoverExpiredLease(execution("patchId"));
        verify(economy).markAiResultUncertain(user,LanguageSuggestionHandler.usage(id,"candidate").idempotencyKey());
    }
    @Test void needsContextIsTerminalAndNeverCallsReviewerOrReplaysInference() throws Exception {
        var d=patch(false);
        when(ai.chat(eq(user),any(),any())).thenReturn(response("no candidate"));
        doAnswer(i->{d.candidateRaw=i.getArgument(2);d.status="NEEDS_CONTEXT";d.reason="行动者不明";return null;}).when(quality).candidate(eq(user),eq(id),anyString());
        suggestion.execute(execution("patchId"));suggestion.execute(execution("patchId"));suggestion.recoverExpiredLease(execution("patchId"));verifyNoInteractions(economy);
        verify(ai,times(1)).chat(eq(user),any(),any());verify(quality,never()).reviewed(any(),any(),any(),any());
    }
    @Test void recoveryAndReviewUseHistoricalProfileAndOriginalUsageKey() throws Exception {
        var d=patch(true);var input=quality.patchInput(user,id);
        when(quality.patchInput(user,id)).thenReturn(new LanguageQualityService.PatchInput(input.manuscriptId(),input.reportId(),id,input.issue(),d,"zh-naturalness-v2"));
        when(ai.chat(eq(user),any(),any())).thenReturn(response("review"));
        suggestion.execute(execution("patchId"));suggestion.recoverExpiredLease(execution("patchId"));
        verify(ai).chat(eq(user),argThat(r->r.messages().get(0).content().startsWith(LanguageStandard.text("zh-naturalness-v2")) && !r.messages().get(0).content().contains("language_examples")),eq(LanguageSuggestionHandler.usage(id,"review","zh-naturalness-v2")));
        verify(economy).markAiResultUncertain(user,LanguageSuggestionHandler.usage(id,"review","zh-naturalness-v2").idempotencyKey());
    }
    @Test void oversizedSuggestionStopsBeforeAnyPaidRequest() throws Exception {
        var d=patch(false);d.beforeContext="甲".repeat(30000);
        suggestion.execute(execution("patchId"));verifyNoInteractions(ai);verify(quality).reviewed(user,id,null,"REVIEW_INCOMPLETE");
    }
    @Test void parsingFailureStopsRemainingChunksAndNeverGeneratesCandidate() throws Exception {
        var d=new ReportData();d.source=new Source(null,1,null,null,null,null,null,LanguageStandard.VERSION);d.projection=LanguageProjection.of("<p>她回来了。</p><p>他开了门。</p>");
        d.coverage=new ArrayList<>(List.of(new Coverage(0,0,5,0,11,"PENDING",null),new Coverage(1,6,11,0,11,"PENDING",null)));
        when(quality.input(user,id)).thenReturn(new LanguageQualityService.CheckInput(UUID.randomUUID(),id,d));
        when(ai.chat(eq(user),any(),any())).thenReturn(response("invalid JSON"));
        new LanguageDiagnosisHandler(quality,ai,economy).execute(execution("reportId"));
        verify(ai,times(1)).chat(eq(user),any(),any());verify(quality).checked(eq(user),eq(id),eq(0),isNull(),contains("解析"));
        verify(quality).finish(user,id);verify(quality,never()).suggest(any(),any(),any(),any());
    }
}
