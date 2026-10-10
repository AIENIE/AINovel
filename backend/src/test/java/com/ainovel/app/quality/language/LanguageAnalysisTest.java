package com.ainovel.app.quality.language;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.ainovel.app.quality.language.LanguageDtos.*;
import static org.junit.jupiter.api.Assertions.*;
class LanguageAnalysisTest {
    @org.junit.jupiter.api.Test void revisionDefaultsToCompleteEvidenceSentencesAndOnlyExpandsParagraphFindings() {
        var p=LanguageProjection.of("<p>她放下书。找这个动作还在。随后她走了。</p>");
        var parsed=LanguageAnalysis.parse("{\"complete\":true,\"issues\":[{\"kind\":\"LANGUAGE\",\"category\":\"AWKWARD\",\"quote\":\"找这个动作还在\",\"impact\":\"抽象化生硬\",\"direction\":\"自然表达\"}]}",p,new LanguageDtos.Coverage(0,0,p.text().length(),0,p.text().length(),"PENDING",null),new com.fasterxml.jackson.databind.ObjectMapper());
        var range=LanguageAnalysis.revisionRange(p,parsed.issues().getFirst());
        org.junit.jupiter.api.Assertions.assertEquals("找这个动作还在。",p.text().substring(range[0],range[1]));
        var paragraph=LanguageAnalysis.parse("{\"complete\":true,\"issues\":[{\"kind\":\"LANGUAGE\",\"category\":\"PARAGRAPH\",\"quote\":\"找这个动作还在\",\"impact\":\"整段生硬\",\"direction\":\"调整全段\"}]}",p,new LanguageDtos.Coverage(0,0,p.text().length(),0,p.text().length(),"PENDING",null),new com.fasterxml.jackson.databind.ObjectMapper());
        var whole=LanguageAnalysis.revisionRange(p,paragraph.issues().getFirst());
        org.junit.jupiter.api.Assertions.assertEquals(p.text(),p.text().substring(whole[0],whole[1]));
    }
    final ObjectMapper json=new ObjectMapper();
    LanguageAnalysis.Parsed parse(String quote,Integer a,Integer b,String text) throws Exception {
        var p=LanguageProjection.of(text); var issue=new LinkedHashMap<String,Object>();
        issue.put("kind","LANGUAGE");issue.put("category","AWKWARD");issue.put("quote",quote);issue.put("impact","搭配生硬");issue.put("direction","恢复人物自然表达");issue.put("start",a);issue.put("end",b);
        return LanguageAnalysis.parse(json.writeValueAsString(Map.of("complete",true,"issues",List.of(issue))),p,new Coverage(0,0,p.text().length(),0,p.text().length(),"PENDING",null),json);
    }
    @Test void ambiguousOrInventedQuoteNeverBecomesApplicable() throws Exception {
        var duplicate=parse("停手",null,null,"她停手。他停手。"); assertFalse(duplicate.complete()); assertEquals("AMBIGUOUS",duplicate.issues().getFirst().location()); assertNull(duplicate.issues().getFirst().start());
        assertEquals("MISSING",parse("不存在",null,null,"原文").issues().getFirst().location());
        assertEquals("INVALID",parse("原文",99,101,"原文").issues().getFirst().location());
        assertEquals("EXACT",parse("停手",5,7,"她停手。他停手。").issues().getFirst().location());
    }
    @Test void supportsUniqueQuoteWithoutModelOffsetsAndEmojiPrefix() throws Exception {
        var issue=parse("找这个动作还在",null,null,"😀找这个动作还在。").issues().getFirst(); assertEquals(2,issue.start()); assertEquals(9,issue.end());
    }
    @Test void longSceneKeepsTailAndRecordsOversizeParagraphRatherThanCutting() {
        var p=LanguageProjection.of("<p>"+"长".repeat(16000)+"</p>"+"<p>短段内容。</p>".repeat(12)+"<p>尾部问题必须保留。</p>");
        var windows=LanguageAnalysis.windows(p,json,20000,30000,24000);
        assertTrue(windows.stream().anyMatch(c->c.state().equals("SKIPPED")));
        assertEquals(p.text().length(),windows.getLast().end());
        assertTrue(windows.stream().anyMatch(c->c.state().equals("PENDING") && p.text().substring(c.start(),c.end()).contains("尾部问题")));
        for(var c:windows) if(c.state().equals("PENDING")) assertTrue(LanguageAnalysis.fits(LanguageAnalysis.request(LanguageAnalysis.DIAGNOSE,LanguageAnalysis.input(p,c,json)),20000,30000,24000));
    }
    @Test void noTwelveIssueCapAndIncompleteJsonCannotMeanNoIssues() throws Exception {
        var p=LanguageProjection.of("她停手。"); var issue=Map.of("kind","STYLE","category","CHOPPY","quote","她停手。","impact","供作者选择","direction","可连写");
        var raw=json.writeValueAsString(Map.of("complete",true,"issues",Collections.nCopies(20,issue)));
        assertEquals(20,LanguageAnalysis.parse(raw,p,new Coverage(0,0,p.text().length(),0,p.text().length(),"PENDING",null),json).issues().size());
        assertThrows(IllegalArgumentException.class,()->LanguageAnalysis.parse("{\"issues\":[",p,new Coverage(0,0,5,0,5,"PENDING",null),json));
        assertThrows(IllegalArgumentException.class,()->LanguageAnalysis.parse("{\"issues\":[]}",p,new Coverage(0,0,5,0,5,"PENDING",null),json));
    }
    @Test void reviewRequiresBothDimensionsAndNoNewCandidate() {
        assertThrows(IllegalArgumentException.class,()->LanguageAnalysis.review("{\"meaning\":\"PASS\"}",json));
        var result=LanguageAnalysis.review("{\"language\":\"PASS\",\"languageReason\":\"顺畅\",\"meaning\":\"FAIL\",\"meaningReason\":\"改变否定\",\"changes\":[\"不去变成去\"]}",json);
        assertEquals(Verdict.FAIL,result.meaning());
    }
}
