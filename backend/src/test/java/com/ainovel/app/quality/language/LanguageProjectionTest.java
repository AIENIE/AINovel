package com.ainovel.app.quality.language;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LanguageProjectionTest {
    @org.junit.jupiter.api.Test void emptyParagraphsAndExplicitConsecutiveBreaksAreNotCollapsed() {
        org.junit.jupiter.api.Assertions.assertEquals("甲\n\n乙",LanguageProjection.of("<p>甲</p><p></p><p>乙</p>").text());
        org.junit.jupiter.api.Assertions.assertEquals("甲\n\n乙\n",LanguageProjection.of("<p>甲<br><br>乙<br></p>").text());
        org.junit.jupiter.api.Assertions.assertEquals("甲\n乙",LanguageProjection.of("<div><p>甲</p><p>乙</p></div>").text());
    }
    @Test void preservesParagraphsWhitespaceEntitiesAndUtf16Coordinates() {
        var p=LanguageProjection.of("<p> 甲&amp;乙&#x1F600;</p><p>丙&nbsp;丁<br>戊</p>");
        assertEquals(" 甲&乙😀\n丙\u00a0丁\n戊",p.text()); assertEquals(3,p.paragraphs().size());
        int emoji=p.text().indexOf("😀"); assertTrue(p.validRange(emoji,emoji+2)); assertFalse(p.validRange(emoji,emoji+1));
        assertEquals("<p> 甲&amp;乙笑</p><p>丙&nbsp;丁<br>戊</p>",p.replace(emoji,emoji+2,"😀","笑"));
        assertNotEquals(p.htmlHash(),p.textHash());
    }
    @Test void paragraphReplacementKeepsAttributesAndUntouchedFormatting() {
        String html="<p class=\"indent\">她停手。翻开，是信。</p><p><strong>原文保留</strong></p>";
        var p=LanguageProjection.of(html); var para=p.paragraphs().getFirst();
        String replaced=p.replace(para.start(),para.end(),para.text(),"她停下手里的活，翻开一看，原来是信。");
        assertTrue(replaced.startsWith("<p class=\"indent\">")); assertTrue(replaced.endsWith("<p><strong>原文保留</strong></p>"));
        assertEquals(html,LanguageProjection.of(replaced).replace(0,"她停下手里的活，翻开一看，原来是信。".length(),"她停下手里的活，翻开一看，原来是信。",para.text()));
    }
    @Test void mixedInlineMarkupAndComplexStructuresAreManualOnly() {
        var mixed=LanguageProjection.of("<p>她<b>停下</b>手。</p>"); assertFalse(mixed.canReplace(0,mixed.text().length()));
        var complex=LanguageProjection.of("<table><tr><td>原文</td></tr></table>"); assertFalse(complex.canReplace(0,2));
        var script=LanguageProjection.of("<p>正文</p><script>隐藏指令</script><p>后文</p>"); assertFalse(script.text().contains("隐藏")); assertFalse(script.simple());
    }
    @Test void lineEndingsAndPlainTextRemainTraceableWithoutTrimming() {
        var p=LanguageProjection.of(" 甲\r\n\r\n乙😀\n"); assertEquals(" 甲\n\n乙😀\n",p.text()); assertEquals(2,p.paragraphs().size());
        assertThrows(IllegalArgumentException.class,()->p.replace(0,2,"错误","替换"));
        assertThrows(IllegalArgumentException.class,()->p.replace(0,p.text().length(),p.text(),"跨段"));
    }
}
