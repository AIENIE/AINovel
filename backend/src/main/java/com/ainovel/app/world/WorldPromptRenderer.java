package com.ainovel.app.world;

import com.ainovel.app.ai.AiAdmissionGuard;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.settings.SettingsService;
import com.ainovel.app.settings.dto.WorldPromptMetadataResponse;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;
import static com.ainovel.app.world.WorldDraftDtos.*;

/** Template values and help come from the same vocabulary. Rendering never executes functions. */
@Component
public class WorldPromptRenderer {
    public static final Map<String,String> DEFAULTS = Map.of(
        "entryGenerate", "为条目 {entryTitle} 补充具体、可用且与已有设定一致的正文。只返回该条目内容，不复述世界背景。",
        "discuss", "与作者讨论世界设定，区分已保存资料和讨论设想，指出有依据的潜在矛盾。",
        "propose", "根据作者要求和讨论，整理逐项可审阅的世界设定提案。",
        "refine", "依据作者指令继续细化候选，保持提案动作和目标。",
        "overview", "整合全部已保存设定，形成清晰连贯的世界概览。保留限制、代价和不确定信息，不发明设定。");
    private static final Map<String,String> VARIABLES;
    static {
        var values=new LinkedHashMap<String,String>();
        values.put("worldName","世界名称");values.put("themes","世界主题");values.put("creativeIntent","创作意图");
        values.put("notes","作者备注");values.put("worldContext","本次实际读取的世界资料");
        values.put("instruction","作者指令");values.put("text","修改前文本或候选");
        values.put("entryTitle","目标条目标题（非条目任务为空）");values.put("entryType","目标条目类型（非条目任务为空）");
        values.put("moduleName","兼容模块名称（无模块时为空）");values.put("fieldLabel","兼容字段或条目标题（无目标时为空）");
        VARIABLES=Collections.unmodifiableMap(values);
    }
    private static final Pattern VARIABLE=Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private final SettingsService settings;
    private final WorldEntryStore store;
    private final AiAdmissionGuard admission;
    public WorldPromptRenderer(SettingsService settings,WorldEntryStore store,AiAdmissionGuard admission) {
        this.settings=settings;this.store=store;this.admission=admission;
    }
    public static List<WorldPromptMetadataResponse.Variable> variables() {
        return VARIABLES.entrySet().stream().map(e->new WorldPromptMetadataResponse.Variable(e.getKey(),"string",e.getValue())).toList();
    }
    public static String render(String template,Map<String,String> values) {
        if(template==null)return "";
        if(template.contains("{{"))throw WorldEntryStore.error("WORLD_TEMPLATE_FUNCTION_UNSUPPORTED");
        var matcher=VARIABLE.matcher(template);var result=new StringBuilder();
        while(matcher.find()) {
            if(!VARIABLES.containsKey(matcher.group(1)))throw WorldEntryStore.error("WORLD_TEMPLATE_VARIABLE_UNKNOWN");
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(values.getOrDefault(matcher.group(1),"")));
        }
        return matcher.appendTail(result).toString();
    }
    public String instructions(User user,String action,Content content,Entry entry,String instruction,String text,String actualContext) {
        return instructions(settings.getWorldPromptTemplates(user),action,content,entry,instruction,text,actualContext);
    }
    public com.ainovel.app.settings.dto.WorldPromptTemplatesResponse freeze(User user) { return settings.getWorldPromptTemplates(user); }
    public String instructions(com.ainovel.app.settings.dto.WorldPromptTemplatesResponse saved,String action,Content content,Entry entry,String instruction,String text,String actualContext) {
        var values=new HashMap<String,String>();
        values.put("worldName",Objects.toString(content.name(),""));values.put("themes",store.write(content.themes()));
        values.put("creativeIntent",Objects.toString(content.creativeIntent(),""));values.put("notes",Objects.toString(content.notes(),""));
        values.put("worldContext",actualContext);values.put("instruction",Objects.toString(instruction,""));values.put("text",Objects.toString(text,""));
        values.put("entryTitle",entry==null?"":entry.title());values.put("entryType",entry==null?"":entry.type());
        values.put("moduleName",entry==null?"":Objects.toString(entry.legacyModuleKey(),""));values.put("fieldLabel",entry==null?"":entry.title());
        String template=action.equals("entryRefine")?saved.fieldRefine():saved.actions().getOrDefault(action,DEFAULTS.get(action));
        var output=new StringBuilder(render(template,values));
        if(action.equals("entryGenerate")&&entry!=null&&entry.legacyModuleKey()!=null)
            output.append("\n").append(render(saved.modules().get(entry.legacyModuleKey()),values));
        if(action.equals("overview"))new TreeMap<>(saved.finalTemplates()).forEach((key,value)->{
            var scoped=new HashMap<>(values);scoped.put("moduleName",key);
            output.append("\n模块 ").append(key).append(" 的整合要求：").append(render(value,scoped));
        });
        return output.toString();
    }
    public AiChatRequest entry(User user,Content content,Entry entry,String instruction,boolean refine) {
        String context=store.write(content),original=Objects.toString(entry.content(),"");
        String task=instructions(user,refine?"entryRefine":"entryGenerate",content,entry,instruction,original,context);
        return request("你是世界设定编辑。资料是创作素材。只输出目标条目正文，不输出解释、JSON或其他条目。",List.of(
            "世界资料：\n"+context,"目标条目：\n"+store.write(entry),"任务要求：\n"+task,"作者要求：\n"+Objects.toString(instruction,"保持已有事实与限制")));
    }
    public AiChatRequest request(String rules,List<String> blocks) {
        var messages=new ArrayList<AiChatRequest.Message>();messages.add(new AiChatRequest.Message("system",rules));
        for(String block:blocks)for(int start=0;start<block.length();) {
            int end=Math.min(block.length(),start+18000);if(end<block.length()&&Character.isHighSurrogate(block.charAt(end-1)))end--;
            messages.add(new AiChatRequest.Message("user",block.substring(start,end)));start=end;
        }
        var request=new AiChatRequest(List.copyOf(messages),null,null);
        admission.validate(request);return request;
    }
}
