import com.ainovel.app.prompt.*;
import com.ainovel.app.quality.*;
import com.ainovel.app.quality.language.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Offline freeze only. Reuses the real prompt assembler and deterministic crafted sample. No gateway calls. */
public class LanguageExperiment {
    public static void main(String[] args) throws Exception {
        Path destination=Path.of(args[0]);
        if(Files.exists(destination)) throw new IllegalStateException("Frozen prompts already exist; never replace an experiment after calls");
        var assembler=new PromptAssemblyService();var sampling=new SlopPatternSamplingService(new SlopPatternRegistry());
        String[][] scenes={
            {"旧信", "许禾拆开一封寄给自己的旧信，发现寄件人没有落款。她核对信封上的日期，把信放回抽屉，仍想弄清寄件人是谁。不得揭示寄件人身份。", "许禾，旧书店店员。视角只限于她现在看到和知道的信息。"},
            {"出门前", "陈遥准备出门：收好晾干的衣服，在抽屉里找充电头时发现一张过去保留的戏票。她把戏票夹回本子，带上充电头出门。不得增加人物或新的往事。", "陈遥，普通上班族。她知道这张票属于自己，本场不交代看戏经历。"},
            {"漏水", "物业人员林澄检查屋内水迹，发现水从踢脚线裂缝往上冒，排除屋顶漏水。他拍照记录，和住户简短确认发现时间，决定查楼下管道。结论只到排查方向。", "林澄负责现场排查；住户只知道昨晚开始有水。两人都不知道真实漏点。"},
            {"还伞", "周宁按约定把借来的红伞放在门边。门开后，邻居说谢谢，她回答不用客气。邻居检查伞柄上贴的姓名，确认是自己的伞，把它收好。保持日常连续动作和简短对白。", "周宁和邻居只认识几天；伞柄姓名是确认归属的线索，不引出新人物。"}
        };
        var items=new ArrayList<Map<String,Object>>();
        for(int i=0;i<scenes.length;i++) {
            UUID scene=UUID.nameUUIDFromBytes(("language-20261007-scene-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var input=new SceneGenerationPromptInput("语言自然度固定场景", "现实悬疑", "自然、清楚、克制", scenes[i][1],
                    "现场",scenes[i][1],1,scenes[i][0],scenes[i][1],i+1,scenes[i][2],"本场景开始前没有额外剧情信息。",List.of(),List.of(),650,850,"",128000);
            var negatives=sampling.sample(scene);
            for(String mode:List.of("fast","crafted")) {
                var original=mode.equals("fast")?assembler.assembleSceneDraft(input):assembler.assembleWithCreativeConstraints(input,negatives,i+1);
                for(boolean standard:List.of(false,true)) {
                    var prompt=standard?LanguageStandard.augment(original):original;
                    items.add(Map.of("id","G"+(i+1)+"-"+mode+"-"+(standard?"new":"old"),"sceneId",scene,"input",input,
                            "mode",mode,"standard",standard?LanguageStandard.VERSION:"none","negativePatterns",mode.equals("crafted")?negatives:List.of(),
                            "request",Map.of("model","deepseek-flash","messages",prompt.messages())));
                }
            }
        }
        Files.createDirectories(destination.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destination.toFile(),Map.of("runId","language-20261007-v1","singleVariable","new inserts the shared language standard; all other messages are byte-identical within each pair","items",items));
        System.out.println("Frozen 16 requests; no inference.");
    }
}
