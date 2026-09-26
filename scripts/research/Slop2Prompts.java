package com.ainovel.app.quality;
import com.ainovel.app.prompt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
/** Actual production prompt assembly, with explicitly isolated research ablations. */
public class Slop2Prompts {
 public static void main(String[] args) throws Exception {
  var json=new ObjectMapper(); var service=new PromptAssemblyService();
  var sampler=new SlopPatternSamplingService(new SlopPatternRegistry());
  Map<String,Object> output=new LinkedHashMap<>();
  for(var t:json.readTree(Files.readString(Path.of(args[0])))) {
   String id=t.path("id").asText();
   var input=new SceneGenerationPromptInput("独立研究场景",t.path("genre").asText(),"自然、连贯","", "研究章","",1,t.path("title").asText(),"",t.path("sceneOrder").asInt(),
    "人物信息仅以冻结上下文为准。","",List.of(),List.of(),550,750,"",128000,t.path("context").asText(),"scene-isolation-research-v1",t.path("contextSha256").asText());
   var p=service.assembleWithCreativeConstraints(input,sampler.sample(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))),t.path("sceneOrder").asInt());
   String system=p.messages().get(0).content(), user=p.messages().get(1).content();
   int a=system.indexOf("禁用的 AI 套路表达"), b=system.indexOf("本节叙事质量目标");
   if(a<0||b<a) throw new IllegalStateException("Production prompt changed");
   String positive=system.substring(0,a)+system.substring(b);
   for(String arm:List.of("base","positive","profile")) {
    String s=arm.equals("base")?system:positive;
    if(arm.equals("profile")) s+="\n目标风格卡（代理构造，不是事实来源）：\n"+t.path("styleCard").asText()+"\n仅示范表达的无关短例（不要复制人物或事件）：\n"+t.path("styleExample").asText();
    output.put(id+"-"+arm,Map.of("messages",List.of(Map.of("role","system","content",s),Map.of("role","user","content",user)),"modelId","deepseek-flash"));
   }
  }
  Files.writeString(Path.of(args[1]),json.writerWithDefaultPrettyPrinter().writeValueAsString(output));
  System.out.println("Frozen 18 requests assembled by production PromptAssemblyService; no model calls.");
 }
}
