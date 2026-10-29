package com.ainovel.app.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ainovel.app.assistant.AssistantDocumentProjection.*;
import static org.assertj.core.api.Assertions.*;

class AssistantDocumentProjectionTest {
    private static String change(Document document, String... paragraphs) {
        return apply(document, List.of(new Replacement(document.runs().getFirst().runId(), List.of(paragraphs))));
    }

    @Test void changesOnlyTheSelectedOccurrenceOfRepeatedText() {
        String source = "<p>一样的话。</p><p>一样的话。</p><p>一样的话。</p>";
        Document document = project(source, new Selection(1, 1, 0, 5));
        assertThat(change(document, "新的话。")).isEqualTo("<p>一样的话。</p><p>新的话。</p><p>一样的话。</p>");
    }

    @Test void decodesEntitiesAndKeepsWhitespaceWithoutTextNormalization() {
        Document document = project("<p>A&nbsp;  &amp; B<br>C &lt; D</p>", null);
        assertThat(document.runs().getFirst().paragraphs()).containsExactly("A\u00a0  & B\nC < D");
        assertThat(change(document, document.runs().getFirst().paragraphs().getFirst()))
                .isEqualTo(document.sourceHtml());
    }

    @Test void utf16SelectionsPreserveWholeEmojiAndRejectSplitSurrogates() {
        Document document = project("<p>甲😀乙𠀀丙</p>", new Selection(0, 0, 1, 3));
        assertThat(document.runs().getFirst().paragraphs()).containsExactly("😀");
        assertThat(change(document, "🙂")).isEqualTo("<p>甲🙂乙𠀀丙</p>");
        assertThatThrownBy(() -> project(document.sourceHtml(), new Selection(0, 0, 2, 3)))
                .hasMessage("ASSISTANT_SELECTION_INVALID");
        assertThatThrownBy(() -> change(document, "\ud83d")).hasMessage("ASSISTANT_REPLACEMENTS_INVALID");
    }

    @Test void preservesUnselectedRichEdgesAcrossParagraphs() {
        Document document = project("<p class='left'>A<strong>BCDE</strong>F</p><p class='right'>G<em>HI</em>J</p>",
                new Selection(0, 1, 3, 2));
        assertThat(document.runs().getFirst().paragraphs()).containsExactly("DEF", "GH");
        String result = change(document, "x", "y");
        var body = Jsoup.parseBodyFragment(result).body();
        assertThat(body.children().get(0).wholeText()).isEqualTo("ABCx");
        assertThat(body.children().get(1).wholeText()).isEqualTo("yIJ");
        assertThat(body.selectFirst("strong").text()).isEqualTo("BC");
        assertThat(body.selectFirst("em").text()).isEqualTo("I");
        assertThat(result).contains("<p class='left'>", "<p class='right'>");
    }

    @Test void splitsMergesAndDeletesParagraphsWithoutTouchingFixedBlocks() {
        String fixed = "<h2 data-x='1'>标题</h2><blockquote><p>引用</p></blockquote><ul><li><p>列表</p></li></ul><pre><code>a &lt; b</code></pre><hr data-x='2'><aside>保留</aside>";
        Document document = project("<p>甲</p><p>乙</p>" + fixed + "<p>丙</p>", null);
        assertThat(document.runs()).hasSize(2);
        String result = apply(document, List.of(
                new Replacement(document.runs().get(0).runId(), List.of("合并")),
                new Replacement(document.runs().get(1).runId(), List.of("一", "二", "三"))));
        assertThat(result).isEqualTo("<p>合并</p>" + fixed + "<p>一</p><p>二</p><p>三</p>");
        assertThat(change(project("<p>甲</p><p>乙</p>", null))).isEqualTo("<p></p>");
        assertThat(apply(document, List.of(new Replacement(document.runs().get(0).runId(), List.of()))))
                .isEqualTo(fixed + "<p>丙</p>");
    }

    @Test void excludesHeadingIndicesButSupportsSelectionAcrossFixedBlocks() {
        String source = "<p>甲乙</p><h2>固定标题</h2><p>丙丁</p>";
        Document document = project(source, new Selection(0, 2, 1, 1));
        assertThat(document.runs()).extracting(Run::startBlockIndex).containsExactly(0, 2);
        String result = apply(document, List.of(new Replacement(document.runs().getFirst().runId(), List.of("一")),
                new Replacement(document.runs().getLast().runId(), List.of("二"))));
        assertThat(result).isEqualTo("<p>甲一</p><h2>固定标题</h2><p>二丁</p>");
        assertThatThrownBy(() -> project(source, new Selection(1, 2, 0, 1))).hasMessage("ASSISTANT_SELECTION_UNSUPPORTED");
    }

    @Test void unknownInlineAtomsAndCommentsCannotBeDiscardedByMerging() {
        String source = "<p>甲<img src='x'></p><p>乙</p><!-- author note --><p>丙</p>";
        Document document = project(source, null);
        assertThat(document.runs()).hasSize(2);
        assertThat(document.runs().getFirst().startBlockIndex()).isEqualTo(1);
        assertThat(change(document, "新")).isEqualTo("<p>甲<img src='x'></p><p>新</p><!-- author note --><p>丙</p>");
    }

    @Test void deletedSelectionMergesOnlyItsUnselectedEdges() {
        Document document = project("<p><strong>甲乙</strong></p><p><em>丙丁</em></p>", new Selection(0, 1, 1, 1));
        assertThat(change(document)).isEqualTo("<p><strong>甲</strong><em>丁</em></p>");
    }

    @Test void preservesUnchangedMarksAndOnlyInheritsCommonChangedMarks() {
        Document document = project("<p>开头<strong>保留</strong>中间<em>保留</em>结尾</p>", null);
        String result = change(document, "新开头保留更改保留新结尾");
        var body = Jsoup.parseBodyFragment(result).body();
        assertThat(body.selectFirst("strong").text()).isEqualTo("保留");
        assertThat(body.selectFirst("em").text()).isEqualTo("保留");
        assertThat(body.text()).isEqualTo("新开头保留更改保留新结尾");
        Document mixed = project("<p><strong>甲<em>乙</em>丙</strong></p>", null);
        assertThat(change(mixed, "新字")).isEqualTo("<p><strong>新字</strong></p>");
    }

    @Test void zeroWidthInsertionKeepsPrefixSuffixAndTheirCommonMarks() {
        Document document = project("<p><strong>甲乙</strong></p>", new Selection(0, 0, 1, 1));
        assertThat(change(document, "插入")).isEqualTo("<p><strong>甲插入乙</strong></p>");
        assertThat(change(document)).isEqualTo(document.sourceHtml());
    }

    @Test void hardBreakOffsetsMatchEditorAndModelHtmlIsOnlyLiteralText() {
        Document document = project("<p>甲<br>乙</p>", new Selection(0, 0, 2, 3));
        assertThat(change(document, "<em>丙</em>&\n丁")).isEqualTo("<p>甲<br>&lt;em&gt;丙&lt;/em&gt;&amp;<br>丁</p>");
    }

    @Test void preservesWholeUnchangedParagraphBytes() {
        Document document = project("<p data-align='left'><strong>甲&nbsp;乙</strong></p><p>丙</p>", null);
        assertThat(change(document, "甲\u00a0乙", "丁")).startsWith("<p data-align='left'><strong>甲&nbsp;乙</strong></p>");
    }

    @Test void emptySceneHasAnInsertionRunButHashRemainsOriginal() {
        Document empty = project("", null), paragraph = project("<p></p>", null);
        assertThat(empty.sourceHtml()).isEmpty();
        assertThat(empty.sourceHash()).isNotEqualTo(paragraph.sourceHash());
        assertThat(empty.runs().getFirst().paragraphs()).containsExactly("");
        assertThat(change(empty, "开始")).isEqualTo("<p>开始</p>");
        assertThat(apply(empty, List.of())).isEqualTo("<p></p>");
    }

    @Test void persistsRoundTripAndRefinesUsingOriginalRunIdentityAfterSplit() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Document document = project("<p>原稿</p><h2>保持</h2><p>第二处</p>", null);
        Document restored = mapper.readValue(mapper.writeValueAsBytes(document), Document.class);
        List<Replacement> parent = List.of(new Replacement(document.runs().getFirst().runId(), List.of("拆一", "拆二")),
                new Replacement(document.runs().getLast().runId(), List.of("旧改动")));
        assertThat(promptRuns(restored, parent).getFirst().paragraphs()).containsExactly("拆一", "拆二");
        List<Replacement> refined = compose(restored, parent,
                List.of(new Replacement(document.runs().getFirst().runId(), List.of("合并后的新稿"))));
        assertThat(apply(restored, refined)).isEqualTo("<p>合并后的新稿</p><h2>保持</h2><p>旧改动</p>");
    }

    @Test void rejectsUnknownDuplicateAndTamperedCoordinates() {
        Document document = project("<p>原稿</p>", null);
        Replacement change = new Replacement(document.runs().getFirst().runId(), List.of("新稿"));
        assertThatThrownBy(() -> apply(document, List.of(new Replacement("run-other", List.of("新稿")))))
                .hasMessage("ASSISTANT_REPLACEMENTS_INVALID");
        assertThatThrownBy(() -> apply(document, List.of(change, change))).hasMessage("ASSISTANT_REPLACEMENTS_INVALID");
        assertThatThrownBy(() -> apply(new Document("<p>已变</p>", document.sourceHash(), null, document.runs()), List.of(change)))
                .hasMessage("ASSISTANT_SOURCE_INVALID");
        assertThatThrownBy(() -> apply(new Document(document.sourceHtml(), document.sourceHash(), null, List.of()), List.of(change)))
                .hasMessage("ASSISTANT_SOURCE_INVALID");
    }

    @Test void refusesAnExpensiveMixedFormatDiffRatherThanLosingUnchangedMarks() {
        Document document = project("<p>" + "甲".repeat(600) + "<strong>必须保留格式</strong>" + "乙".repeat(600) + "</p>", null);
        assertThatThrownBy(() -> change(document, "丙".repeat(600) + "必须保留格式" + "丁".repeat(600)))
                .hasMessage("ASSISTANT_FORMAT_PRESERVATION_LIMIT");
        Document plain = project("<p>" + "甲".repeat(600) + "</p>", null);
        assertThat(change(plain, "乙".repeat(600))).isEqualTo("<p>" + "乙".repeat(600) + "</p>");
        Document uniform = project("<p><strong>" + "甲".repeat(600) + "</strong></p>", null);
        assertThat(change(uniform, "乙".repeat(600))).isEqualTo("<p><strong>" + "乙".repeat(600) + "</strong></p>");
    }
}
