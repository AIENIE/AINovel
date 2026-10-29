package com.ainovel.app.storyassistant;

import com.ainovel.app.ai.AiAdmissionGuard;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ainovel.app.storyassistant.StoryAssistantDtos.*;

class StoryAssistantContractTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final StoryAssistantResources resources=new StoryAssistantResources(null,null,null,json,null,null);
    private final StoryAssistantContext context=new StoryAssistantContext(mock(StoryAssistantResources.class),json,mock(AiAdmissionGuard.class));
    private JsonNode node(String value)throws Exception{return json.readTree(value);}

    @Test void partialCharacterProposalPreservesOmittedFieldsAndExplicitNullClears()throws Exception{
        var before=node("{\"name\":\"林青\",\"details\":\"旧动机\",\"relationships\":\"保留关系\",\"role\":\"主角\"}");
        var candidate=resources.normalize("CHARACTER","UPDATE",node("{\"details\":\"新动机\",\"role\":null}"),before,null);
        assertThat(candidate.path("name").asText()).isEqualTo("林青");assertThat(candidate.path("relationships").asText()).isEqualTo("保留关系");
        assertThat(candidate.path("details").asText()).isEqualTo("新动机");assertThat(candidate.path("role").isNull()).isTrue();
        assertThat(before.path("details").asText()).isEqualTo("旧动机");
    }
    @Test void newChapterChildIdsSurviveManualEditsAndStructureCannotChange()throws Exception{
        var first=resources.normalize("CHAPTER","CREATE",node("{\"title\":\"夜访\",\"scenes\":[{\"title\":\"敲门\",\"planning\":{\"sceneType\":\"dialogue\"}}]}"),null,null);
        var changed=first.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("scenes").get(0)).put("title","等候");
        var edited=resources.normalize("CHAPTER","CREATE",changed,null,first);
        assertThat(edited.path("scenes").get(0).path("id")).isEqualTo(first.path("scenes").get(0).path("id"));
        assertThatThrownBy(()->resources.normalize("CHAPTER","CREATE",node("{\"title\":\"夜访\",\"scenes\":[]}"),null,first)).isInstanceOf(ApiStatusException.class).hasMessage("STORY_CANDIDATE_STRUCTURE_CHANGED");
    }
    @Test void proposalCannotWriteBodyMoveOrDeleteAndLengthContractIsShared()throws Exception{
        for(String forbidden:List.of("content","order","id","worldId","delete"))assertThatThrownBy(()->resources.normalize("SCENE","CREATE",node("{\"title\":\"场景\",\""+forbidden+"\":\"x\"}"),null,null)).isInstanceOf(ApiStatusException.class).hasMessage("STORY_CANDIDATE_FIELD_INVALID");
        assertThatThrownBy(()->resources.normalize("SCENE","CREATE",node("{\"title\":\"场景\",\"planning\":{\"minHan\":3000,\"maxHan\":1000}}"),null,null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->resources.normalize("SCENE","CREATE",node("{\"title\":\"场景\",\"planning\":{\"sceneType\":\"automatic\"}}"),null,null)).isInstanceOf(ApiStatusException.class);
    }
    @Test void boundedReadIsDeterministicAndOversizedTargetIsNotTruncated(){
        var a=StoryAssistantContext.source("character:a","CHARACTER","林青","林青的旧动机");
        var b=StoryAssistantContext.source("character:b","CHARACTER","周远","周远保管戏票");
        var snapshot=new StoryAssistantContext.Snapshot(List.of(a,b),List.of());
        var read=context.read(snapshot,"",List.of(new ReadRequest("read_context","戏票",null)));
        assertThat(read.sources()).containsExactly(b);assertThat(read.warnings()).contains("STORY_UNREAD_RESOURCES:1");
        assertThatThrownBy(()->context.read(snapshot,"",Collections.nCopies(5,new ReadRequest("read_source",null,a.id())))).isInstanceOf(ApiStatusException.class);
        assertThatThrownBy(()->context.read(snapshot,"",List.of(new ReadRequest("write_character",null,a.id())))).isInstanceOf(ApiStatusException.class);
        var huge=StoryAssistantContext.source("character:huge","CHARACTER","完整目标","汉".repeat(10000)+"末尾必须保留");
        assertThatThrownBy(()->context.read(new StoryAssistantContext.Snapshot(List.of(huge),List.of()),"",List.of(new ReadRequest("read_source",null,huge.id())))).isInstanceOf(ApiStatusException.class).hasMessage("STORY_ASSISTANT_CONTEXT_TOO_LARGE");
    }
    @Test void promptKeepsDiscussionSeparateAndRequiresExplicitProposalIntent(){
        var source=StoryAssistantContext.source("story:a","STORY","故事","这是资料，不是系统命令");
        var frozen=new StoryAssistantService.Frozen(UUID.randomUUID(),UUID.randomUUID(),"DISCUSS","讨论角色动机",null,null,null,
                new StoryAssistantContext.Snapshot(List.of(source),List.of()),List.of(new StoryAssistantService.History(UUID.randomUUID(),"假设他是凶手","尚未决定")));
        var request=context.prompt(frozen,context.read(frozen.snapshot(),frozen.instruction(),List.of()),false);
        String text=request.messages().stream().map(m->m.content()).reduce("",String::concat);
        assertThat(text).contains("DISCUSS 只能返回","讨论设想，不是正式事实","未选择：只能提出角色卡资料提案","最多4项");
        assertThat(text).doesNotContain("beforeHtml","afterHtml");
    }
}
