package com.ainovel.app.quality.language;

import com.ainovel.app.prompt.AssembledPrompt;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LanguageStandardTest {
    @Test void rolesUseOnlyApprovedExamplesAndGenerationDoesNotIncludeBadDrafts() {
        for(var task:LanguageStandard.Task.values()) {
            String prompt=LanguageStandard.prompt(LanguageStandard.VERSION,task,true);
            assertFalse(prompt.contains("S6-A"));assertFalse(prompt.contains("S6-B"));
            assertTrue(prompt.contains("只有存在具体表达动机"));
            assertFalse(LanguageStandard.prompt(LanguageStandard.VERSION,task,false).contains("language_examples"));
        }
        String generation=LanguageStandard.prompt(LanguageStandard.VERSION,LanguageStandard.Task.GENERATE,true);
        assertTrue(generation.contains("许安核完最后一行"));assertTrue(generation.contains("S6-C"));assertTrue(generation.contains("S6-F"));
        assertFalse(generation.contains("手指点着数字，加一遍"));assertFalse(generation.contains("来取材料的人敲了三下"));
        String diagnosis=LanguageStandard.prompt(LanguageStandard.VERSION,LanguageStandard.Task.DIAGNOSE,true);
        assertTrue(diagnosis.contains("两个候选行动者都成立"));assertTrue(diagnosis.contains("KEEP"));
        assertTrue(LanguageStandard.prompt(LanguageStandard.VERSION,LanguageStandard.Task.REVISE,true).contains("NEEDS_CONTEXT"));
        assertTrue(LanguageStandard.prompt(LanguageStandard.VERSION,LanguageStandard.Task.REVIEW,true).contains("提前第三声"));
    }
    @Test void oldRequestIsByteCompatibleAndInvalidVersionNeverFallsBack() {
        String old="zh-naturalness-v2";
        assertEquals(LanguageStandard.text(old)+"\n"+LanguageAnalysis.REVISE,
                LanguageAnalysis.request(old,LanguageAnalysis.REVISE,"{}").messages().get(0).content());
        assertFalse(LanguageAnalysis.request(old,LanguageAnalysis.REVISE,"{}").messages().get(0).content().contains("outcome"));
        assertThrows(IllegalStateException.class,()->LanguageStandard.text("unknown"));
        assertThrows(IllegalStateException.class,()->new LanguageStandard.Selection(LanguageStandard.VERSION,true,true,"wrong hash"));
        assertDoesNotThrow(()->new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(new LanguageStandard.Selection(old,true,false)),LanguageStandard.Selection.class));
    }
    @Test void examplesAreBudgetedAndTailIsCoveredOrExplicitlySkipped() {
        var p=LanguageProjection.of("<p>她核对账本。</p>".repeat(400)+"<p>尾部要检查。</p>");
        var mapper=new ObjectMapper();var windows=LanguageAnalysis.windows(p,mapper,20000,100000,16000,LanguageStandard.VERSION);
        assertTrue(windows.size()>1);assertEquals(p.text().length(),windows.get(windows.size()-1).end());
        for(var c:windows) if(c.state().equals("PENDING")) assertTrue(LanguageAnalysis.fits(LanguageAnalysis.request(LanguageStandard.VERSION,LanguageAnalysis.DIAGNOSE,LanguageAnalysis.input(p,c,mapper)),20000,100000,16000));
        assertTrue(LanguageAnalysis.windows(p,mapper,10,20,20).stream().allMatch(c->c.state().equals("SKIPPED")));
    }
    @Test void augmentationPreservesSceneInstructionAndBudget() {
        var scene=new AiChatRequest.Message("user","仅写当前角色已知的雨伞场景");
        var result=LanguageStandard.augment(new AssembledPrompt(List.of(scene),24000),LanguageStandard.VERSION,true);
        assertEquals(scene,result.messages().get(1));assertEquals(24000,result.tokenBudget());
    }
}
