package com.ainovel.app.modelconfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AiModelSelectionTest {
    static AiModelSelection tokens(boolean paid,String input,String output) {
        return new AiModelSelection("generic.chat","option","model","Visible plan","DEFAULT",null,true,paid,
                "TOKENS",null,new BigDecimal(input),new BigDecimal(output),1);
    }
    @ParameterizedTest @CsvSource({"0,0,1","1,1,1","50000,50000,1","50001,50000,2","-1,1,1"})
    void preservesLegacyRoundingWithoutCountingCachedOrReasoningTokensAgain(long input,long output,long expected) {
        assertThat(tokens(true,"0.01","0.01").cost(input,output)).isEqualTo(expected);
    }
    @Test void combinesDifferentRetailRatesBeforeRounding() {
        assertThat(tokens(true,"0.1","0.2").cost(1000,4000)).isEqualTo(1);
        assertThat(tokens(true,"0.1","0.2").cost(1000,4501)).isEqualTo(2);
    }
    @Test void freeDoesNotApplyMinimumOneAndFixedPriceDoesNotDependOnUsage() {
        assertThat(tokens(false,"5","12").cost(100000,100000)).isZero();
        var fixed=new AiModelSelection("generic.chat","id","model","Deep","ON","HIGH",false,true,"FIXED",7L,null,null,2);
        assertThat(fixed.cost(0,0)).isEqualTo(7);
        assertThat(fixed.cost(999999,999999)).isEqualTo(7);
    }
    @Test void unknownThinkingDoesNotMeanExplicitlyDisabledAndCannotOfferAnEffort() {
        var unknown=AiModelConfigurationService.Thinking.unknown();
        assertThatCode(()->AiModelConfigurationService.validateThinking(unknown,"DEFAULT",null)).doesNotThrowAnyException();
        assertThatThrownBy(()->AiModelConfigurationService.validateThinking(unknown,"OFF",null)).hasMessage("AI_THINKING_UNSUPPORTED");
        assertThatThrownBy(()->AiModelConfigurationService.validateThinking(unknown,"ON","LOW")).hasMessage("AI_THINKING_UNSUPPORTED");
        var enabledOnly=new AiModelConfigurationService.Thinking(true,true,false,List.of(new AiModelConfigurationService.ThinkingOption("HIGH","High")),null,null);
        assertThatCode(()->AiModelConfigurationService.validateThinking(enabledOnly,"ON",null)).doesNotThrowAnyException();
        assertThatCode(()->AiModelConfigurationService.validateThinking(enabledOnly,"ON","HIGH")).doesNotThrowAnyException();
        assertThatThrownBy(()->AiModelConfigurationService.validateThinking(enabledOnly,"ON","LOW")).hasMessage("AI_THINKING_UNSUPPORTED");
        assertThatThrownBy(()->AiModelConfigurationService.validateThinking(enabledOnly,"OFF",null)).hasMessage("AI_THINKING_UNSUPPORTED");
    }
    @Test void contextIsRestoredAndCallIdentitiesAreStableAcrossResumption() {
        var outer=new AiSelectionContext.Snapshot("quote-a",Map.of("generic.chat",tokens(true,"1","1")));
        String first;
        try(var ignored=AiSelectionContext.install(outer)) {
            first=AiSelectionContext.callKey("generic.chat");
            assertThat(AiSelectionContext.callKey("generic.chat")).isNotEqualTo(first);
            assertThatThrownBy(()->AiSelectionContext.selection("plot.revise")).hasMessage("AI_STEP_NOT_QUOTED");
            try(var child=AiSelectionContext.install(new AiSelectionContext.Snapshot("quote-b",Map.of()))) {assertThat(AiSelectionContext.current().identity()).isEqualTo("quote-b");}
            assertThat(AiSelectionContext.current()).isEqualTo(outer);
        }
        assertThat(AiSelectionContext.current()).isNull();
        try(var resumed=AiSelectionContext.install(outer)) {assertThat(AiSelectionContext.callKey("generic.chat")).isEqualTo(first);}
    }
    @Test void functionalRegistryDoesNotIncludeUnimplementedOrSpecializedCapabilities() {
        assertThat(AiFeatures.all()).extracting(AiFeatures.Definition::key).doesNotHaveDuplicates()
                .contains("scene.fast.length","language.review","story_assistant.propose.final","world.magic_tech.rules.generate")
                .doesNotContain("beta_reader","continuity_check","embedding","rerank");
        assertThat(AiFeatures.all()).allSatisfy(f->{assertThat(f.key().length()).isLessThanOrEqualTo(50);assertThat(f.zh()).isNotBlank();assertThat(f.tw()).isNotBlank();assertThat(f.en()).isNotBlank();});
    }
}
