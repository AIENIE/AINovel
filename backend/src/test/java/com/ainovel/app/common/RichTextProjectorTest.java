package com.ainovel.app.common;

import com.ainovel.app.common.text.RichTextProjector;
import com.ainovel.app.common.text.RichTextProjector.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RichTextProjectorTest {
    @Test
    void versionedViewsKeepTheirHistoricalEntityAndWhitespaceContract() {
        String html = "<p>甲&amp;乙 &quot;引号&quot;&#x4FE1;</p><p>丙<br />丁</p>";
        assertEquals("甲&乙 &quot;引号&quot;&#x4FE1;丙丁", text(html, Policy.QUALITY_V1));
        assertEquals("甲&乙 \"引号\"&#x4FE1; 丙 丁", text(html, Policy.CONTEXT_V2));
        assertEquals("甲&乙 \"引号\"信\n\n丙\n丁", text(html, Policy.EXPORT_V1));
        assertEquals(List.of("甲&乙 \"引号\"信", "丙\n丁"), RichTextProjector.evidenceBlocks(html));
    }

    @Test
    void evidenceOffsetsDoNotBecomeUtf16WhenTextContainsSupplementaryCharacters() {
        String html = "<p>甲😀e\u0301甲</p><p>重复引文  重复引文</p>";
        List<String> blocks = RichTextProjector.evidenceBlocks(html);
        assertEquals(List.of("甲😀e\u0301甲", "重复引文 重复引文"), blocks);
        assertEquals(5, blocks.getFirst().codePointCount(0, blocks.getFirst().length()));
        assertEquals(6, blocks.getFirst().length());
        assertEquals(RichTextProjector.OffsetUnit.UNICODE_CODE_POINT, Policy.EVIDENCE_V1.offsetUnit());
        assertEquals(RichTextProjector.OffsetUnit.UTF16_CODE_UNIT, Policy.QUALITY_V1.offsetUnit());
    }

    @Test
    void hiddenMarkupAndBlockIdentifiersRemainEvidenceCompatible() {
        assertEquals("", text("\u2000", Policy.CONTEXT_V2));
        assertEquals("\u2000", text("\u2000", Policy.QUALITY_V1));
        assertEquals(List.of("甲", "乙", "丙"), RichTextProjector.evidenceBlocks(
                "<html><head><style>private</style></head><body><p>甲</p><script>private</script><ul><li>乙</li><li>丙</li></ul></body></html>"));
        for (Policy policy : Policy.values()) {
            assertEquals("", text(null, policy));
            assertFalse(policy.version().isBlank());
        }
    }

    private String text(String html, Policy policy) { return RichTextProjector.project(html, policy).text(); }
}
