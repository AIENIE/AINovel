package com.ainovel.app.writingagent;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.assistant.AssistantDocumentProjection;
import com.ainovel.app.assistant.AssistantDtos;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateAgentToolsTest {
    private final ObjectMapper json=new ObjectMapper();
    private final CandidateAgentTools tools=new CandidateAgentTools(json,20000,100000,24000);

    @Test void templateFingerprintIsIndependentOfMapperOrderingFormattingAndAdmissionLimits() {
        String expected=tools.templateHash();
        var differentlyConfigured=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT,SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        var other=new CandidateAgentTools(differentlyConfigured,100,200,300);
        assertThat(other.templateHash()).isEqualTo(expected);
        assertThat(new CandidateAgentTools(new ObjectMapper(),20000,100000,24000).templateHash()).isEqualTo(expected);
        assertThat(tools.templateHash()).isEqualTo(expected).matches("[0-9a-f]{64}");
    }

    @Test void oneShotRequestsUseDistinctExistingStepIdentitiesAndFrozenDraftMaterial() {
        var input=input("<p>盐港入夜了。</p>",List.of(),List.of());
        var draft=tools.draftRequest(input,"crafted");var refine=tools.refineRequest(input);
        assertThat(draft.featureKey()).isEqualTo("scene.crafted.draft");assertThat(refine.featureKey()).isEqualTo("writing_agent.refine");
        assertThat(draft.messages().getFirst().content()).isEqualTo("已冻结的场景模板与作者边界");
        assertThat(draft.messages().toString()).contains("原可编辑组ID","盐港入夜了");
        assertThat(draft.toolsJson()).isNull();
    }
    @Test void replacingSelectedRunPreservesParentEditsAndFixedHtml() throws Exception {
        String original="<p><strong>门开了。</strong></p><table><tbody><tr><td>固定表格</td></tr></tbody></table><p>盐港入夜了。</p>";
        var document=AssistantDocumentProjection.project(original,null);
        var parent=List.of(new AssistantDocumentProjection.Replacement(document.runs().getFirst().runId(),List.of("门缓缓打开了。")));
        String parentHtml=AssistantDocumentProjection.apply(document,parent);
        var input=new CandidateAgentTools.Input("完善末段",parentHtml,List.of(),List.of(),50,100,false,document,parent);
        String raw=json.writeValueAsString(Map.of("replacements",List.of(Map.of("runId",document.runs().getLast().runId(),"paragraphs",List.of("盐港的夜风穿过街巷。")))));
        var candidate=tools.parseCandidate(raw,input);
        assertThat(candidate.html()).contains("门缓缓打开了。","<table><tbody><tr><td>固定表格</td></tr></tbody></table>","盐港的夜风穿过街巷。");
        assertThat(candidate.replacements()).hasSize(2);assertThat(candidate.actualHan()).isPositive();
    }
    @Test void invalidAndNoChangeCandidatesNeverBecomeReplacementResults() throws Exception {
        var input=input("<p>盐港入夜了。</p>",List.of(),List.of());
        assertThatThrownBy(()->tools.parseCandidate("{\"replacements\":[]}",input)).isInstanceOf(ApiStatusException.class).hasMessage("WRITING_AGENT_CANDIDATE_INVALID");
        String invented=json.writeValueAsString(Map.of("replacements",List.of(Map.of("runId","invented","paragraphs",List.of("新文本")))));
        assertThatThrownBy(()->tools.parseCandidate(invented,input)).hasMessage("WRITING_AGENT_CANDIDATE_INVALID");
        String same=json.writeValueAsString(Map.of("replacements",List.of(Map.of("runId",input.document().runs().getFirst().runId(),"paragraphs",List.of("盐港入夜了。")))));
        assertThatThrownBy(()->tools.parseCandidate(same,input)).hasMessage("WRITING_AGENT_NO_CHANGE");
    }
    @Test void languageCheckUsesOnlyCompleteCandidateAndDoesNotReadWorldOrCreateFormalReport() {
        var secret=new AssistantDtos.Source("world:secret","WORLD_RULE","秘密标题","不能发给语言诊断的秘密",null,"hash",false,"read");
        var input=input("<p>盐港入夜了。</p>",List.of(secret),List.of());
        var request=tools.languageRequest(input);
        assertThat(request.featureKey()).isEqualTo("language.diagnose");
        assertThat(request.messages().toString()).contains("盐港入夜了").doesNotContain("不能发给语言诊断的秘密","秘密标题");
        var report=tools.parseLanguage("{\"complete\":true,\"summary\":\"未发现明确问题\",\"issues\":[]}",input);
        assertThat(report.kind()).isEqualTo("LANGUAGE");assertThat(report.candidateHash()).isNotBlank();
    }
    @Test void h2CandidateAndPlotRequestsExcludeUnclassifiedWorldSources() {
        String html="<p>林青推开门。</p>";var doc=AssistantDocumentProjection.project(html,null);
        var source=new AssistantDtos.Source("world:secret","WORLD_EVENT","秘密事件","秘密事件正文和时代",null,"hash",false,"read");
        var input=new CandidateAgentTools.Input("完善动作",html,List.of(),List.of(source),50,100,true,doc,List.of());
        assertThat(tools.refineRequest(input).messages().toString()).doesNotContain("秘密事件","秘密事件正文和时代");
        assertThat(tools.plotRequest(input).messages().toString()).doesNotContain("秘密事件","秘密事件正文和时代");
    }
    @Test void completePlotInputIsNotCutAtHistoricalSevenThousandCharactersAndEvidenceIsChecked() {
        String html="<p>"+"潮水退去。".repeat(1500)+"全文末端标记。</p>";
        var input=input(html,List.of(),List.of());var generous=new CandidateAgentTools(json,50000,100000,100000);
        assertThat(generous.plotRequest(input).messages().getLast().content()).endsWith("全文末端标记。");
        var result=generous.parsePlot("{\"complete\":true,\"summary\":\"发现疑点\",\"issues\":[{\"dimension\":\"CAUSALITY\",\"evidence\":\"并不存在的引文\",\"why_it_matters\":\"无法判断\",\"minimal_fix\":\"询问作者\",\"certainty\":\"CONFIRMED\"}]}",input);
        assertThat(result.data().path("complete").asBoolean()).isFalse();
        assertThat(result.data().path("issues").get(0).path("certainty").asText()).isEqualTo("UNCERTAIN");
        assertThat(result.data().path("issues").get(0).path("evidenceAvailable").asBoolean()).isFalse();
    }
    @Test void oversizedCandidateBlocksBeforeARequestIsReturnedInsteadOfWindowingOrTruncating() {
        var input=input("<p>"+"完整内容".repeat(5000)+"</p>",List.of(),List.of());
        assertThatThrownBy(()->tools.languageRequest(input)).hasMessage("WRITING_AGENT_TOOL_INPUT_LIMIT");
        assertThatThrownBy(()->tools.plotRequest(input)).hasMessage("WRITING_AGENT_TOOL_INPUT_LIMIT");
        assertThatThrownBy(()->tools.refineRequest(input)).hasMessage("WRITING_AGENT_TOOL_INPUT_LIMIT");
    }
    private CandidateAgentTools.Input input(String html,List<AssistantDtos.Source> reads,List<AssistantDocumentProjection.Replacement> parent) {
        var document=AssistantDocumentProjection.project(html,null);
        return new CandidateAgentTools.Input("完善当前场景",html,List.of(new AiChatRequest.Message("system","已冻结的场景模板与作者边界")),reads,50,100,false,document,parent);
    }
}
