import com.ainovel.app.quality.language.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Read-only offline checks of the already frozen and paid experiment. Never sends requests. */
public class LanguageExamplesEvidence {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args[0]);var mapper=new ObjectMapper();
        var frozen=mapper.readTree(dir.resolve("frozen-prompts.json").toFile());
        if(!frozen.path("profileHash").asText().equals(LanguageStandard.hash("zh-naturalness-v3")))
            throw new IllegalStateException("Frozen profile changed after calls");
        for(var task:LanguageStandard.Task.values()) for(boolean examples:List.of(false,true))
            if(!frozen.path("profiles").path(task.name()).path(examples?"examples":"rules").asText()
                    .equals(LanguageStandard.prompt("zh-naturalness-v3",task,examples))) throw new IllegalStateException("Role changed");
        var results=new ArrayList<Map<String,Object>>();
        var cases=mapper.readTree(dir.resolve("frozen-diagnostic-cases.json").toFile());
        for(String name:List.of("S5","S6","new-problems","new-boundary")) for(String arm:List.of("rules","examples")) {
            String text=cases.path(name).asText();
            // Offline inputs use double newlines; retain their exact coordinates, not the HTML projection's separator.
            var raw=mapper.readTree(dir.resolve("result-diagnosis-"+name+"-"+arm+".json").toFile()).path("response").path("content").asText();
            var parsed=LanguageAnalysis.json(raw,mapper);
            int exact=0, ambiguous=0, missing=0;
            for(var issue:parsed.path("issues")) {
                String quote=issue.path("quote").asText();int at=text.indexOf(quote);
                if(at<0) missing++; else if(text.indexOf(quote,at+1)>=0) ambiguous++; else exact++;
            }
            results.add(Map.of("case",name,"arm",arm,"complete",parsed.path("complete").asBoolean(),
                    "issues",parsed.path("issues").size(),"uniqueContinuousQuotes",exact,"ambiguous",ambiguous,"missing",missing));
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("offline-evidence-checks.json").toFile(),
                Map.of("profileUnchanged",true,"rolePromptsUnchanged",true,"diagnosticQuotes",results,
                        "scope","Mechanical evidence check only; model complete=true is not proof of recall."));
        System.out.println("Frozen profile and 8 diagnosis outputs checked offline; no inference.");
    }
}
