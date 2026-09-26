package com.ainovel.app.narrative;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.manuscript.*;
import com.ainovel.app.manuscript.context.*;
import com.ainovel.app.prompt.*;
import com.ainovel.app.quality.*;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Local opt-in fixture export: frozen input and actual old/new builders; never calls AI. */
class H2FrozenRetryExportTest {
    @Test
    @EnabledIfSystemProperty(named="h23.exportRetries",matches="true")
    void exportLengthRetriesWithoutRecompilingOrTuningFrozenContext() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();
        var root=Path.of("../artifacts/h23-quality-20260922");
        var names=Set.of("com.ainovel.app.manuscript.SceneGenerationPromptBuilder","com.ainovel.app.prompt.PromptAssemblyService","com.ainovel.app.manuscript.SceneGenerationContext");
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{root.resolve("baseline-classes").toUri().toURL()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    if(names.stream().noneMatch(n->name.equals(n)||name.startsWith(n+"$")))return super.loadClass(name,resolve);
                    var c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
                }
            }
        }) {
            for(int number=1;number<=6;number++)for(String version:List.of("baseline","candidate"))for(var mode:GenerationMode.values()) {
                String label="H2-A"+number+"-"+version+"-"+mode;
                var frozen=json.readTree(root.resolve("requests/H2-A"+number+".json").toFile()).path(version+"-"+mode);
                int attempt=Integer.getInteger("h23.retryAttempt",2);
                if(attempt>2 && version.equals("candidate"))continue;
                var response=root.resolve("comparisons/"+label+(attempt==2?"":"-retry"+(attempt-1))+".json");
                if(!Files.exists(response))continue;
                var result=json.readTree(response.toFile());assertEquals(200,result.path("status").asInt());
                String draft=result.path("body").path("content").asText().trim();
                int count=(int)draft.codePoints().filter(cp->Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN).count();
                if(count>=600 && count<=900)continue;
                var output=root.resolve("retry-requests/"+label+"-retry"+attempt+".json");
                if(Files.exists(output))continue; // Previously frozen requests remain byte-for-byte intact.
                var tree=(ObjectNode)frozen.path("preview").deepCopy();tree.remove("promptVersion");
                var p=json.treeToValue(tree,NarrativeContextDtos.Preview.class);
                String compiler=frozen.path("promptVersion").asText();
                var manifest=new SceneDraftContextManifest(compiler,p.contextHash(),"SHA-256",p.tokenBudget(),p.tokenUsed(),null,null,p.stamp().manuscriptId(),p.sceneId(),null,1,2,"",List.of(),List.of(),p.stamp());
                var compiled=new CompiledSceneDraftContext(p.content(),manifest,"",List.of(),List.of(),List.of(),"");
                var cl=version.equals("baseline")?loader:getClass().getClassLoader();
                var builder=cl.loadClass("com.ainovel.app.manuscript.SceneGenerationPromptBuilder").getConstructor().newInstance();
                var assembly=cl.loadClass("com.ainovel.app.prompt.PromptAssemblyService").getConstructor().newInstance();
                ReflectionTestUtils.setField(builder,"promptAssemblyService",assembly);
                ReflectionTestUtils.setField(builder,"slopPatternSamplingService",new SlopPatternSamplingService(new SlopPatternRegistry()));
                var sceneClass=cl.loadClass("com.ainovel.app.manuscript.SceneGenerationContext");
                var ctor=sceneClass.getDeclaredConstructor(UUID.class,String.class,String.class,Integer.class,String.class,String.class,Integer.class,List.class,List.class);ctor.setAccessible(true);
                var scene=ctor.newInstance(p.sceneId(),"隐藏标题","隐藏摘要",1,"隐藏场景","隐藏规划",2,List.of(),List.of());
                var prompt=(AssembledPrompt)builder.getClass().getMethod("build",User.class,Story.class,sceneClass,String.class,String.class,String.class,int.class,int.class,int.class,int.class,GenerationMode.class,CompiledSceneDraftContext.class)
                        .invoke(builder,null,null,scene,"隐藏人物","隐藏前文",draft,count,attempt,600,900,mode,compiled);
                if(version.equals("candidate"))assertTrue(prompt.messages().stream().map(AiChatRequest.Message::content).anyMatch(s->s.contains(draft)),"The complete previous draft and ending must survive");
                Files.createDirectories(output.getParent());
                Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("messages",prompt.messages(),"previousHan",count,"attempt",attempt,"contextHash",p.contextHash(),"promptVersion",compiler)));
            }
        }
    }
}
