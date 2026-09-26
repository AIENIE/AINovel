package com.ainovel.app.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Evidence probes of existing behavior, not proposed production implementations. */
public class SlopProbes {
    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper();
        LocalSlopHeuristics detector = new LocalSlopHeuristics();
        Map<String,Object> results = new LinkedHashMap<>();
        String repeated = "甲乙丙丁戊己庚辛壬癸甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉天地玄黄宇宙洪荒日月";
        var repetition = new SlopRepetitionDetector();
        results.put("ngramAligned", repetition.detect(repeated));
        results.put("ngramShiftedByOne", repetition.detect("序" + repeated));

        String text = "这不是勇气，而是坚持。这不是恐惧，而是成长。这不是终点，而是起点。这不是告别，而是开始。这不是选择，而是命运。";
        results.put("withoutContextHint", detector.evaluate(text));
        results.put("withGlobalHint", detector.evaluate(new SlopHeuristicInput(text,"","","侦探分析腔；排除法","","","","")));
        results.put("leadingWhitespace", detector.evaluate("  " + text));

        SlopQualityGate gate = new SlopQualityGate(detector,null,null,null);
        Method safer = SlopQualityGate.class.getDeclaredMethod("isSaferRevision",SlopHeuristicResult.class,SlopHeuristicResult.class);
        safer.setAccessible(true);
        results.put("deletionPassesLocalAcceptance",safer.invoke(gate,detector.evaluate(text),detector.evaluate("他走了。")));

        AiSlopJudgeClient judge = new AiSlopJudgeClient(null,json);
        Method parse = AiSlopJudgeClient.class.getDeclaredMethod("parseIssues",Object.class,String.class);
        parse.setAccessible(true);
        results.put("invalidQuoteAccepted",parse.invoke(judge,List.of(Map.of("quote","原文中不存在的句子","char_start",999,"char_end",1000,"severity","high","risk_score",90)),"他走了。"));
        results.put("duplicateQuoteUsesFirst",parse.invoke(judge,List.of(Map.of("quote","他走了。","severity","high","risk_score",90)),"他走了。她留下。他走了。"));

        String longText = "甲".repeat(7100) + "尾部唯一证据：红伞归还。";
        var req = new SlopQualityRequest(null,null,null,"作品","悬疑","克制","章","场","","前文","角色","风格",longText);
        Method jp = AiSlopJudgeClient.class.getDeclaredMethod("buildPrompt",SlopQualityRequest.class,SlopHeuristicResult.class);
        jp.setAccessible(true);
        results.put("judgeIncludesTail",((String)jp.invoke(judge,req,detector.evaluate(longText))).contains("尾部唯一证据"));
        var diagnostic = new SlopDiagnosticService(null,json,detector,null,null,null,null);
        Method dp = SlopDiagnosticService.class.getDeclaredMethod("buildPrompt",SlopQualityRequest.class,SlopHeuristicResult.class);
        dp.setAccessible(true);
        results.put("diagnosticIncludesTail",((String)dp.invoke(diagnostic,req,detector.evaluate(longText))).contains("尾部唯一证据"));
        var revision = new AiConservativeRevisionService(null);
        Method rp = AiConservativeRevisionService.class.getDeclaredMethod("buildPrompt",SlopQualityRequest.class,SlopJudgeResult.class);
        rp.setAccessible(true);
        results.put("revisionIncludesTail",((String)rp.invoke(revision,req,new SlopJudgeResult(90,true,List.of(),"修订"))).contains("尾部唯一证据"));
        Files.writeString(Path.of(args[0]),json.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        System.out.println("Recorded baseline probes; no network or database calls.");
    }
}
