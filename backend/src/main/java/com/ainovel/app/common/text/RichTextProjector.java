package com.ainovel.app.common.text;

import org.springframework.web.util.HtmlUtils;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One owner for text projections. Policies are versioned because whitespace/entity handling
 * forms part of saved hashes and offsets. Never silently change an existing policy: introduce
 * a new version and migrate its consumers explicitly. These are intentionally different views.
 */
public final class RichTextProjector {
    public enum OffsetUnit { UTF16_CODE_UNIT, UNICODE_CODE_POINT }
    public enum Policy {
        QUALITY_V1("quality-plain-v1", OffsetUnit.UTF16_CODE_UNIT),
        CONTEXT_V2("context-plain-v2", OffsetUnit.UTF16_CODE_UNIT),
        EXPORT_V1("export-plain-v1", OffsetUnit.UTF16_CODE_UNIT),
        EVIDENCE_V1("narrative-evidence-v1", OffsetUnit.UNICODE_CODE_POINT);
        private final String version;
        private final OffsetUnit offsetUnit;
        Policy(String version, OffsetUnit offsetUnit) { this.version = version; this.offsetUnit = offsetUnit; }
        public String version() { return version; }
        public OffsetUnit offsetUnit() { return offsetUnit; }
    }

    public record Projection(Policy policy, String text) { }
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Set<String> BLOCK_TAGS = Set.of("p", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "tr", "blockquote");
    private RichTextProjector() { }

    public static Projection project(String html, Policy policy) {
        if (policy == null) throw new IllegalArgumentException("TEXT_PROJECTION_POLICY_REQUIRED");
        String value = html == null ? "" : html;
        String text = switch (policy) {
            case QUALITY_V1 -> commonEntities(TAGS.matcher(value).replaceAll("")).trim();
            case CONTEXT_V2 -> context(value);
            case EXPORT_V1 -> export(value);
            case EVIDENCE_V1 -> String.join("\n", evidenceBlocks(value));
        };
        return new Projection(policy, text);
    }

    private static String commonEntities(String value) {
        return value.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private static String context(String value) {
        if (value.isBlank()) return "";
        return commonEntities(TAGS.matcher(value).replaceAll(" "))
                .replace("&quot;", "\"").replace("&#39;", "'").replaceAll("\\s+", " ").trim();
    }

    private static String export(String value) {
        String plain = value.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(?:p|div|h[1-6]|blockquote)\\s*>", "\n\n")
                .replaceAll("(?i)</li\\s*>", "\n");
        return HtmlUtils.htmlUnescape(TAGS.matcher(plain).replaceAll(""))
                .replace('\u00a0', ' ').replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    /** Per-block positions stay code-point based; joining blocks is not an offset conversion. */
    public static List<String> evidenceBlocks(String html) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            private int hidden;
            private boolean boundary(HTML.Tag tag) { return BLOCK_TAGS.contains(tag.toString()); }
            private void endBlock() {
                String value = current.toString().replace('\u00a0', ' ').strip();
                if (!value.isBlank()) blocks.add(value);
                current.setLength(0);
            }
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE || tag == HTML.Tag.HEAD) hidden++;
                if (hidden == 0 && boundary(tag)) endBlock();
            }
            @Override public void handleEndTag(HTML.Tag tag, int pos) {
                if (hidden == 0 && boundary(tag)) endBlock();
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE || tag == HTML.Tag.HEAD) hidden = Math.max(0, hidden - 1);
            }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (hidden == 0 && tag == HTML.Tag.BR) current.append('\n');
            }
            @Override public void handleText(char[] data, int pos) { if (hidden == 0) current.append(data); }
            @Override public void flush() { endBlock(); }
        };
        try {
            new ParserDelegator().parse(new StringReader(html == null ? "" : html), callback, true);
            callback.flush();
        } catch (Exception ex) {
            throw new IllegalArgumentException("NARRATIVE_INVALID_HTML");
        }
        return List.copyOf(blocks);
    }
}
