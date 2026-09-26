package com.ainovel.app.quality;
import com.ainovel.app.prompt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
/** Research-only: change precisely the production narrative-goal block. */
public class Slop3Prompts {
 public static void main(String[] args) throws Exception {
  var json=new ObjectMapper(); var service=new PromptAssemblyService();
  var sampler=new SlopPatternSamplingService(new SlopPatternRegistry());
  Map<String,Object> output=new LinkedHashMap<>();
  var dest=Path.of(args[1]); if(Files.exists(dest)) throw new IllegalStateException("Already frozen");
  for(var t:json.readTree(Files.readString(Path.of(args[0])))) {
   String id=t.path("id").asText();
   var input=new SceneGenerationPromptInput("独立研究场景",t.path("genre").asText(),"自然、连贯","", "研究章","",1,t.path("title").asText(),"",t.path("sceneOrder").asInt(),
    "人物信息仅以冻结上下文为准。","",List.of(),List.of(),550,750,"",128000,t.path("context").asText(),"scene-isolation-research-v1",t.path("contextSha256").asText());
   var p=service.assembleWithCreativeConstraints(input,sampler.sample(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))),t.path("sceneOrder").asInt());
   String system=p.messages().get(0).content(), user=p.messages().get(1).content();
   String header="本节叙事质量目标（请重点体现以下要素）：";
   int start=system.indexOf(header);
   if(start<0 || system.indexOf(header,start+1)>=0) throw new IllegalStateException("Goal block changed");
   String suffix=system.substring(start+header.length());
   if(suffix.lines().filter(s->!s.isBlank()).count()!=2) throw new IllegalStateException("Unexpected goal block structure");
   String adapted=system.substring(0,start)+header+"\n- "+t.path("sceneGoals").get(0).asText()+"\n- "+t.path("sceneGoals").get(1).asText()+"\n";
   for(int replicate=1;replicate<=2;replicate++) for(String arm:List.of("A","B"))
    output.put(id+"-"+arm+replicate,Map.of("messages",List.of(Map.of("role","system","content",arm.equals("A")?system:adapted),Map.of("role","user","content",user)),"modelId","deepseek-flash"));
  }
  Files.writeString(dest,json.writerWithDefaultPrettyPrinter().writeValueAsString(output));
  System.out.println("Frozen 32 production-assembled research requests, no model calls.");
 }
}
