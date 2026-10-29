package com.ainovel.app.worldcontext;

import com.ainovel.app.story.model.Outline;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class WorldContextQuery {
    private final ObjectMapper json;
    public WorldContextQuery(ObjectMapper json){this.json=json;}
    public JsonNode tree(Outline outline) {try{return json.readTree(outline.getContentJson());}catch(Exception e){throw new IllegalStateException("OUTLINE_CONTENT_INVALID",e);}}
    public JsonNode node(Outline outline,UUID nodeId) {
        if(nodeId==null)return json.createObjectNode();
        for(var chapter:tree(outline).path("chapters")) {
            if(nodeId.toString().equals(chapter.path("id").asText()))return chapter;
            for(var scene:chapter.path("scenes"))if(nodeId.toString().equals(scene.path("id").asText()))return scene;
        }throw WorldContextSelector.error("WORLD_CONTEXT_NODE_MISSING");
    }
    public String outline(Outline o,UUID nodeId,String instruction,String text) {
        var s=o.getStory();var root=tree(o);var previous=new ArrayList<String>();
        var chapters=root.path("chapters");int index=0;
        for(var chapter:chapters) {
            boolean target=nodeId!=null&&(chapter.path("id").asText().equals(nodeId.toString())||java.util.stream.StreamSupport.stream(chapter.path("scenes").spliterator(),false).anyMatch(scn->scn.path("id").asText().equals(nodeId.toString())));
            if(target||index>=chapters.size()-2)previous.add(chapter.path("title").asText()+"："+chapter.path("summary").asText());index++;
        }
        return "故事："+s.getTitle()+"\n题材："+s.getGenre()+"\n梗概："+s.getSynopsis()+"\n全书规划："+root.path("planning")+
                "\n已有章节："+String.join("\n",previous)+"\n当前目标："+node(o,nodeId)+"\n作者要求："+Objects.toString(instruction,"")+"\n目标文本："+Objects.toString(text,"");
    }
}
