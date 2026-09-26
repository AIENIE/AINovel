package com.ainovel.app.narrative;

import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.ainovel.app.narrative.NarrativeDtos.*;

/** Versioned, server-owned evidence text. Offsets count Unicode code points, not UTF-16 units. */
public final class NarrativeText {
    private NarrativeText() {}
    public static List<Block> blocks(String html) {
        List<Block> blocks = new ArrayList<>();
        for (String value : com.ainovel.app.common.text.RichTextProjector.evidenceBlocks(html)) {
            blocks.add(new Block("b" + (blocks.size() + 1), value));
        }
        return List.copyOf(blocks);
    }

    public static Evidence resolve(List<Block> blocks, Evidence requested) {
        if (requested == null || requested.quote() == null || requested.quote().isBlank()) throw invalid("NARRATIVE_EVIDENCE_REQUIRED");
        Block block = blocks.stream().filter(b -> b.id().equals(requested.blockId())).findFirst()
                .orElseThrow(() -> invalid("NARRATIVE_EVIDENCE_BLOCK_MISSING"));
        String quote = requested.quote();
        int start = block.text().indexOf(quote);
        if (start < 0) throw invalid("NARRATIVE_EVIDENCE_MISMATCH");
        if (block.text().indexOf(quote, start + 1) >= 0) throw invalid("NARRATIVE_EVIDENCE_AMBIGUOUS");
        if ((start > 0 && Character.isLowSurrogate(block.text().charAt(start)))
                || (start + quote.length() < block.text().length() && Character.isLowSurrogate(block.text().charAt(start + quote.length())))) {
            throw invalid("NARRATIVE_EVIDENCE_MISMATCH");
        }
        return new Evidence(block.id(), quote, block.text().codePointCount(0, start),
                block.text().codePointCount(0, start + quote.length()));
    }

    public static String textHash(List<Block> blocks) {
        return hash(String.join("\n\n", blocks.stream().map(Block::text).toList()));
    }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public static Map<UUID, Position> positions(JsonNode root) {
        List<JsonNode> chapters = sorted(root.path("chapters"));
        Map<UUID, Position> result = new LinkedHashMap<>();
        List<String> prefix = new ArrayList<>();
        for (int ci = 0; ci < chapters.size(); ci++) {
            JsonNode chapter = chapters.get(ci);
            UUID chapterId = uuid(chapter.path("id").asText());
            List<JsonNode> scenes = sorted(chapter.path("scenes"));
            for (int si = 0; si < scenes.size(); si++) {
                JsonNode scene = scenes.get(si);
                UUID sceneId = uuid(scene.path("id").asText());
                if (sceneId == null || chapterId == null || result.containsKey(sceneId)) throw invalid("NARRATIVE_INVALID_OUTLINE");
                prefix.add(chapterId + ":" + sceneId);
                result.put(sceneId, new Position(chapterId, chapter.path("title").asText(), ci + 1,
                        sceneId, scene.path("title").asText(), si + 1, prefix.size() - 1, hash(String.join("/", prefix))));
            }
        }
        return result;
    }

    private static List<JsonNode> sorted(JsonNode values) {
        List<JsonNode> items = new ArrayList<>();
        if (values.isArray()) values.forEach(items::add);
        items.sort(Comparator.comparingInt(n -> n.path("order").asInt(0)));
        return items;
    }
    private static UUID uuid(String text) { try { return UUID.fromString(text); } catch (IllegalArgumentException ex) { return null; } }
    static ApiStatusException invalid(String code) { return new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY, code); }
}
