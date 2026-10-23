package com.ainovel.app.modelconfig;

import java.util.*;

/** Server-owned functional identities, shared by configuration, quotation and callers. */
public final class AiFeatures {
    public record Definition(String key,String group,String zh,String tw,String en,boolean retired) {
        public Definition(String key,String group,String zh,String tw,String en) {
            this(key,group,zh,tw,en,"slop.revise".equals(key));
        }
    }
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static {
        add("story.conception","creation","故事构思","故事構思","Story conception");
        add("story.refine","creation","故事润色","故事潤色","Refine story");
        add("character.refine","creation","角色润色","角色潤色","Refine character");
        add("outline.chapter","creation","章节大纲生成","章節大綱生成","Generate chapter outline");
        add("outline.chapter_refine","creation","章节摘要润色","章節摘要潤色","Refine chapter summary");
        add("outline.scene_refine","creation","场景摘要润色","場景摘要潤色","Refine scene summary");
        String[][] modules={{"geography","地理环境","地理環境","Geography","terrain","地形地貌","Terrain","climate","气候特征","Climate","locations","重要地点","Locations"},
                {"society","社会体系","社會體系","Society","politics","政治体制","Politics","economy","经济结构","Economy","culture","文化","Culture"},
                {"magic_tech","魔法/科技","魔法/科技","Magic / technology","system_name","体系名称","System name","rules","规则","Rules","limitations","限制","Limitations"}};
        for(var m:modules) for(int i=4;i<m.length;i+=3) for(String operation:List.of("generate","refine")) {
            String verb=operation.equals("generate")?"生成":"润色",traditional=operation.equals("generate")?"生成":"潤色";
            add("world."+m[0]+"."+m[i]+"."+operation,"world",m[1]+"·"+m[i+1]+verb,m[2]+"·"+traditional(m[i+1])+traditional,m[3]+" · "+m[i+2]+" · "+operation);
        }
        add("world.entry.generate","world","世界条目补空","世界條目補空","Fill world entry");
        add("world.entry.refine","world","世界条目精修","世界條目精修","Refine world entry");
        String[][] guided={{"premise","故事前提","故事前提","Premise"},{"world","世界观候选","世界觀候選","World candidates"},
                {"characters","角色候选","角色候選","Character candidates"},{"outline","大纲候选","大綱候選","Outline candidates"},
                {"outline_develop","大纲发展","大綱發展","Develop outline"},{"outline_rewrite","大纲重写","大綱重寫","Rewrite outline"},
                {"outline_expand","大纲扩展","大綱擴展","Expand outline"},{"json_repair","格式修复","格式修復","Repair JSON"}};
        for(var g:guided) add("guided."+g[0],"guided",g[1],g[2],g[3]);
        for(String mode:List.of("fast","crafted")) {
            String name=mode.equals("fast")?"快速":"精雕";
            add("scene."+mode+".draft","writing",name+"正文起草",name+"正文起草",mode+" scene draft");
            add("scene."+mode+".length","writing",name+"篇幅修订",name+"篇幅修訂",mode+" length revision");
        }
        for(var intent:List.of("auto","edit","author_qa")) for(var stage:List.of("plan","final"))
            add("assistant."+intent+"."+stage,"assistant","正文助手·"+intentName(intent)+"·"+stageName(stage),"正文助手·"+traditional(intentName(intent))+"·"+(stage.equals("plan")?"規劃":"輸出"),"Writing assistant · "+intent+" · "+stage);
        for(var intent:List.of("discuss","propose","refine")) for(var stage:List.of("plan","final"))
            add("story_assistant."+intent+"."+stage,"assistant","故事助手·"+intentName(intent)+"·"+stageName(stage),"故事助手·"+traditional(intentName(intent))+"·"+(stage.equals("plan")?"規劃":"輸出"),"Story assistant · "+intent+" · "+stage);
        String[][] quality={{"slop.judge","语义审校","語義審校","Semantic review"},{"slop.revise","保守修订","保守修訂","Conservative revision"},
                {"slop.diagnose","Slop 诊断","Slop 診斷","Slop diagnosis"},{"slop.drift","长篇漂移分析","長篇漂移分析","Drift analysis"},
                {"plot.diagnose","剧情诊断","劇情診斷","Plot diagnosis"},{"plot.revise","剧情修订","劇情修訂","Plot revision"},
                {"language.diagnose","语言诊断","語言診斷","Language diagnosis"},{"language.revise","语言修订","語言修訂","Language revision"},
                {"language.review","语言复核","語言複核","Language review"},{"narrative.extract","叙事状态提取","敘事狀態提取","Narrative extraction"}};
        for(var q:quality) add(q[0],"quality",q[1],q[2],q[3]);
        add("generic.chat","tools","通用对话","通用對話","General chat");
        add("generic.refine","tools","通用润色","通用潤色","General refinement");
        add("versions.summary","tools","版本差异摘要","版本差異摘要","Version difference summary");
    }
    private AiFeatures() {}
    private static void add(String k,String g,String z,String t,String e) { DEFINITIONS.put(k,new Definition(k,g,z,t,e)); }
    private static String stageName(String stage) {return stage.equals("plan")?"规划":"输出";}
    private static String intentName(String intent) {return switch(intent){case "auto"->"自动";case "edit"->"修改";case "author_qa"->"问答";case "discuss"->"讨论";case "propose"->"提案";default->"打磨";};}
    private static String traditional(String value) {return value.replace("政治体制","政治體制").replace("气候特征","氣候特徵").replace("重要地点","重要地點").replace("经济结构","經濟結構").replace("体系名称","體系名稱").replace("规则","規則").replace("自动","自動").replace("问答","問答").replace("讨论","討論");}
    public static List<Definition> all() {return List.copyOf(DEFINITIONS.values());}
    public static Definition require(String key) {
        var found=DEFINITIONS.get(key); if(found==null) throw new IllegalArgumentException("Unknown AI feature: "+key); return found;
    }
}
