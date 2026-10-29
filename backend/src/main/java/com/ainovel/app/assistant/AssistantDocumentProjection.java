package com.ainovel.app.assistant;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Frozen, server-owned edit coordinates. Model output contains text, never replacement HTML. */
public final class AssistantDocumentProjection {
    private AssistantDocumentProjection() {}

    public record Selection(int startBlockIndex, int endBlockIndex, int startOffset, int endOffset) {}
    public record Run(String runId, int startBlockIndex, int endBlockIndex, List<String> paragraphs) {}
    public record Document(String sourceHtml, String sourceHash, Selection selection, List<Run> runs) {}
    public record Replacement(String runId, List<String> paragraphs) {}

    private record Mark(String open, String close) {}
    private record Letter(int point, List<Mark> marks, boolean hardBreak) {}
    private record Block(int index, int start, int end, String open, String close, List<Letter> letters,
                         boolean editable) {
        String text() { return string(letters); }
    }
    private record Region(Run run, List<Block> blocks, int from, int to) {}
    private record Parsed(String html, List<Block> blocks, List<Region> regions) {}
    private record Patch(int from, int to, String html) {}
    private static final Set<String> MARKS = Set.of("strong", "b", "em", "i", "u", "s", "strike", "del",
            "code", "a", "span", "mark", "sub", "sup");

    public static Document project(String sourceHtml, Selection selection) {
        String source = sourceHtml == null ? "" : sourceHtml;
        Parsed parsed = parse(source, selection);
        return new Document(source, hash(source), selection, parsed.regions().stream().map(Region::run).toList());
    }

    /** Returns editable parent-candidate text using the original run identity after paragraph splits/merges. */
    public static List<Run> promptRuns(Document document, List<Replacement> replacements) {
        Document original = checked(document);
        Map<String, Replacement> changes = changes(original, replacements);
        return original.runs().stream().map(run -> new Run(run.runId(), run.startBlockIndex(), run.endBlockIndex(),
                changes.containsKey(run.runId()) ? changes.get(run.runId()).paragraphs() : run.paragraphs())).toList();
    }

    /** A follow-up replaces named runs in its parent; omitted parent changes remain present. */
    public static List<Replacement> compose(Document document, List<Replacement> parent, List<Replacement> edits) {
        Document original = checked(document);
        Map<String, Replacement> combined = new LinkedHashMap<>(changes(original, parent));
        combined.putAll(changes(original, edits));
        return original.runs().stream().filter(r -> combined.containsKey(r.runId())).map(r -> combined.get(r.runId())).toList();
    }

    public static String apply(Document document, List<Replacement> replacements) {
        Document original = checked(document);
        Map<String, Replacement> changes = changes(original, replacements);
        Parsed parsed = parse(original.sourceHtml(), original.selection());
        List<Patch> patches = new ArrayList<>();
        for (Region region : parsed.regions()) {
            Replacement replacement = changes.get(region.run().runId());
            if (replacement == null || replacement.paragraphs().equals(region.run().paragraphs())) continue;
            List<Block> blocks = region.blocks();
            patches.add(new Patch(blocks.getFirst().start(), blocks.getLast().end(), render(region, replacement.paragraphs(), parsed.html())));
        }
        StringBuilder result = new StringBuilder(parsed.html());
        for (int i = patches.size() - 1; i >= 0; i--) {
            Patch patch = patches.get(i);
            result.replace(patch.from(), patch.to(), patch.html());
        }
        return result.toString().isBlank() ? "<p></p>" : result.toString();
    }

    private static Document checked(Document document) {
        if (document == null || document.sourceHtml() == null || !hash(document.sourceHtml()).equals(document.sourceHash()))
            throw invalid("ASSISTANT_SOURCE_INVALID");
        Document original = project(document.sourceHtml(), document.selection());
        if (!original.runs().equals(document.runs())) throw invalid("ASSISTANT_SOURCE_INVALID");
        return original;
    }

    private static Map<String, Replacement> changes(Document document, List<Replacement> replacements) {
        if (replacements == null) throw invalid("ASSISTANT_REPLACEMENTS_INVALID");
        Set<String> ids = new HashSet<>();
        document.runs().forEach(run -> ids.add(run.runId()));
        Map<String, Replacement> changes = new LinkedHashMap<>();
        for (Replacement replacement : replacements) {
            if (replacement == null || !ids.contains(replacement.runId()) || replacement.paragraphs() == null
                    || changes.putIfAbsent(replacement.runId(), replacement) != null)
                throw invalid("ASSISTANT_REPLACEMENTS_INVALID");
            replacement.paragraphs().forEach(AssistantDocumentProjection::validUnicode);
        }
        return changes;
    }

    private static Parsed parse(String source, Selection selection) {
        String html = source.isBlank() ? "<p></p>" : source;
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        List<Node> roots = parser.parseFragmentInput(html, new Element("body"), "");
        List<Block> blocks = new ArrayList<>();
        for (Node node : roots) {
            if (!(node instanceof Element element)) continue;
            int start = element.sourceRange().start().pos();
            int end = element.endSourceRange().end().pos();
            boolean tracked = element.sourceRange().isTracked() && !element.sourceRange().isImplicit()
                    && element.endSourceRange().isTracked() && !element.endSourceRange().isImplicit()
                    && start >= 0 && end <= html.length() && end >= start;
            List<Letter> letters = new ArrayList<>();
            boolean editable = tracked && "p".equals(element.normalName()) && read(element, html, List.of(), letters);
            String open = tracked ? html.substring(start, element.sourceRange().end().pos()) : "";
            String close = tracked ? html.substring(element.endSourceRange().start().pos(), end) : "";
            blocks.add(new Block(blocks.size(), start, end, open, close, List.copyOf(letters), editable));
        }
        if (selection != null) validateSelection(selection, blocks);
        List<Region> regions = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        for (Block block : blocks) {
            boolean selected = block.editable() && (selection == null || block.index() >= selection.startBlockIndex()
                    && block.index() <= selection.endBlockIndex());
            boolean adjacent = current.isEmpty() || block.index() == current.getLast().index() + 1
                    && html.substring(current.getLast().end(), block.start()).isBlank();
            if (!selected || !adjacent) {
                addRegion(regions, current, selection);
                current.clear();
            }
            if (selected) current.add(block);
        }
        addRegion(regions, current, selection);
        return new Parsed(html, List.copyOf(blocks), List.copyOf(regions));
    }

    private static void addRegion(List<Region> regions, List<Block> blocks, Selection selection) {
        if (blocks.isEmpty()) return;
        Block first = blocks.getFirst(), last = blocks.getLast();
        int from = selection != null && first.index() == selection.startBlockIndex() ? selection.startOffset() : 0;
        int to = selection != null && last.index() == selection.endBlockIndex() ? selection.endOffset() : last.text().length();
        List<String> paragraphs = new ArrayList<>();
        for (Block block : blocks) paragraphs.add(block.text().substring(block == first ? from : 0, block == last ? to : block.text().length()));
        Run run = new Run("run-" + first.index() + "-" + last.index() + "-" + from + "-" + to,
                first.index(), last.index(), List.copyOf(paragraphs));
        regions.add(new Region(run, List.copyOf(blocks), from, to));
    }

    private static void validateSelection(Selection selection, List<Block> blocks) {
        if (selection.startBlockIndex() < 0 || selection.endBlockIndex() < selection.startBlockIndex()
                || selection.endBlockIndex() >= blocks.size()) throw invalid("ASSISTANT_SELECTION_INVALID");
        Block first = blocks.get(selection.startBlockIndex()), last = blocks.get(selection.endBlockIndex());
        if (!first.editable() || !last.editable()) throw invalid("ASSISTANT_SELECTION_UNSUPPORTED");
        boundary(first.text(), selection.startOffset());
        boundary(last.text(), selection.endOffset());
        if (first == last && selection.endOffset() < selection.startOffset()) throw invalid("ASSISTANT_SELECTION_INVALID");
    }

    private static void boundary(String text, int offset) {
        if (offset < 0 || offset > text.length() || offset > 0 && offset < text.length()
                && Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset)))
            throw invalid("ASSISTANT_SELECTION_INVALID");
    }

    private static boolean read(Element element, String html, List<Mark> marks, List<Letter> letters) {
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode text) {
                text.getWholeText().codePoints().forEach(point -> letters.add(new Letter(point, marks, false)));
            } else if (child instanceof Element inline) {
                if ("br".equals(inline.normalName())) letters.add(new Letter('\n', marks, true));
                else {
                    if (!MARKS.contains(inline.normalName()) || !inline.sourceRange().isTracked()
                            || !inline.endSourceRange().isTracked() || inline.endSourceRange().isImplicit()) return false;
                    List<Mark> nested = new ArrayList<>(marks);
                    nested.add(new Mark(html.substring(inline.sourceRange().start().pos(), inline.sourceRange().end().pos()),
                            html.substring(inline.endSourceRange().start().pos(), inline.endSourceRange().end().pos())));
                    if (!read(inline, html, List.copyOf(nested), letters)) return false;
                }
            } else return false;
        }
        return true;
    }

    private static String render(Region region, List<String> paragraphs, String source) {
        List<Block> blocks = region.blocks();
        List<Letter> before = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            before.addAll(slice(block, i == 0 ? region.from() : 0, i == blocks.size() - 1 ? region.to() : block.text().length()));
            if (i + 1 < blocks.size()) before.add(new Letter(-1, List.of(), false));
        }
        List<Integer> after = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            paragraphs.get(i).codePoints().forEach(after::add);
            if (i + 1 < paragraphs.size()) after.add(-1);
        }
        int[] mapping = matches(before, after.stream().mapToInt(Integer::intValue).toArray());
        List<List<Letter>> rendered = new ArrayList<>();
        if (!paragraphs.isEmpty()) rendered.add(new ArrayList<>());
        for (int i = 0; i < after.size();) {
            if (mapping[i] >= 0) {
                add(rendered, after.get(i), before.get(mapping[i]));
                i++;
                continue;
            }
            int start = i;
            while (i < after.size() && mapping[i] < 0) i++;
            int oldStart = start == 0 ? 0 : mapping[start - 1] + 1;
            int oldEnd = i == after.size() ? before.size() : mapping[i];
            List<Mark> marks = commonMarks(before, oldStart, oldEnd);
            if (before.isEmpty() && blocks.size() == 1) {
                int cursor = blocks.getFirst().text().codePointCount(0, region.from());
                marks = commonMarks(blocks.getFirst().letters(), cursor, cursor);
            }
            for (int j = start; j < i; j++) add(rendered, after.get(j), new Letter(after.get(j), marks, after.get(j) == '\n'));
        }
        List<Letter> prefix = slice(blocks.getFirst(), 0, region.from());
        List<Letter> suffix = slice(blocks.getLast(), region.to(), blocks.getLast().text().length());
        if (rendered.isEmpty() && (!prefix.isEmpty() || !suffix.isEmpty())) rendered.add(new ArrayList<>());
        if (!rendered.isEmpty()) {
            rendered.getFirst().addAll(0, prefix);
            rendered.getLast().addAll(suffix);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < rendered.size(); i++) {
            List<Letter> paragraph = rendered.get(i);
            Block corresponding = rendered.size() == blocks.size() ? blocks.get(i) : null;
            if (corresponding != null && paragraph.equals(corresponding.letters())) {
                result.append(source, corresponding.start(), corresponding.end());
                continue;
            }
            Block retained = corresponding != null ? corresponding : i == 0 && !prefix.isEmpty() ? blocks.getFirst()
                    : i == rendered.size() - 1 && !suffix.isEmpty() ? blocks.getLast() : null;
            result.append(retained == null ? "<p>" : retained.open());
            result.append(renderLetters(paragraph));
            result.append(retained == null ? "</p>" : retained.close());
        }
        return result.toString();
    }

    private static void add(List<List<Letter>> paragraphs, int point, Letter letter) {
        if (point == -1) paragraphs.add(new ArrayList<>());
        else paragraphs.getLast().add(letter);
    }

    private static List<Letter> slice(Block block, int from, int to) {
        int start = block.text().codePointCount(0, from), end = block.text().codePointCount(0, to);
        return block.letters().subList(start, end);
    }

    private static List<Mark> commonMarks(List<Letter> before, int from, int to) {
        // Insertions inherit only marks shared on both sides of the insertion point.
        if (from == to) { from = Math.max(0, from - 1); to = Math.min(before.size(), to + 1); }
        List<Mark> common = null;
        for (int i = from; i < to; i++) {
            if (before.get(i).point() == -1) continue;
            if (common == null) common = new ArrayList<>(before.get(i).marks());
            else common.retainAll(before.get(i).marks());
        }
        return common == null ? List.of() : List.copyOf(common);
    }

    private static String renderLetters(List<Letter> letters) {
        StringBuilder html = new StringBuilder();
        List<Mark> active = List.of();
        for (Letter letter : letters) {
            int same = 0;
            while (same < active.size() && same < letter.marks().size() && active.get(same).equals(letter.marks().get(same))) same++;
            for (int i = active.size() - 1; i >= same; i--) html.append(active.get(i).close());
            for (int i = same; i < letter.marks().size(); i++) html.append(letter.marks().get(i).open());
            active = letter.marks();
            if (letter.hardBreak()) html.append("<br>");
            else if (letter.point() == '&') html.append("&amp;");
            else if (letter.point() == '<') html.append("&lt;");
            else if (letter.point() == '>') html.append("&gt;");
            else html.appendCodePoint(letter.point());
        }
        for (int i = active.size() - 1; i >= 0; i--) html.append(active.get(i).close());
        return html.toString();
    }

    /** Myers equal-letter mapping. A costly mixed-format rewrite is rejected instead of losing marks. */
    private static int[] matches(List<Letter> beforeLetters, int[] after) {
        int[] before = beforeLetters.stream().mapToInt(Letter::point).toArray();
        int[] result = new int[after.length];
        Arrays.fill(result, -1);
        int prefix = 0;
        while (prefix < before.length && prefix < after.length && before[prefix] == after[prefix]) {
            result[prefix] = prefix; prefix++;
        }
        int oldEnd = before.length, newEnd = after.length;
        while (oldEnd > prefix && newEnd > prefix && before[oldEnd - 1] == after[newEnd - 1]) result[--newEnd] = --oldEnd;
        int n = oldEnd - prefix, m = newEnd - prefix;
        if (n == 0 || m == 0) return result;
        int limit = Math.min(n + m, 512), offset = limit + 1;
        int[] frontier = new int[limit * 2 + 3];
        List<int[]> trace = new ArrayList<>();
        for (int d = 0; d <= limit; d++) {
            trace.add(frontier.clone());
            for (int k = -d; k <= d; k += 2) {
                int index = offset + k;
                int x = k == -d || k != d && frontier[index - 1] < frontier[index + 1] ? frontier[index + 1] : frontier[index - 1] + 1;
                int y = x - k;
                while (x < n && y < m && before[prefix + x] == after[prefix + y]) { x++; y++; }
                frontier[index] = x;
                if (x >= n && y >= m) {
                    for (int step = d; step >= 0; step--) {
                        int[] previous = trace.get(step);
                        int diagonal = x - y, previousK = diagonal == -step || diagonal != step
                                && previous[offset + diagonal - 1] < previous[offset + diagonal + 1] ? diagonal + 1 : diagonal - 1;
                        int previousX = previous[offset + previousK], previousY = previousX - previousK;
                        while (x > previousX && y > previousY) result[prefix + --y] = prefix + --x;
                        x = previousX; y = previousY;
                    }
                    return result;
                }
            }
        }
        Set<List<Mark>> remainingMarks = new HashSet<>();
        for (int i = prefix; i < oldEnd; i++) if (beforeLetters.get(i).point() != -1) remainingMarks.add(beforeLetters.get(i).marks());
        if (remainingMarks.size() > 1) throw invalid("ASSISTANT_FORMAT_PRESERVATION_LIMIT");
        return result;
    }

    private static String string(List<Letter> letters) {
        StringBuilder text = new StringBuilder();
        letters.forEach(letter -> text.appendCodePoint(letter.point()));
        return text.toString();
    }

    private static void validUnicode(String text) {
        if (text == null) throw invalid("ASSISTANT_REPLACEMENTS_INVALID");
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw invalid("ASSISTANT_REPLACEMENTS_INVALID");
            } else if (Character.isLowSurrogate(value) || value == 0) throw invalid("ASSISTANT_REPLACEMENTS_INVALID");
        }
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
