package com.ainovel.app.material;

import com.ainovel.app.material.model.Material;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class MaterialChunker {
    private static final int CHUNK_SIZE = 900;
    private static final int OVERLAP = 120;

    public List<MaterialChunk> chunks(Material material) {
        if (material == null || material.getId() == null || material.getContent() == null || material.getContent().isBlank()) {
            return List.of();
        }
        String content = normalize(material.getContent());
        List<MaterialChunk> chunks = new ArrayList<>();
        int seq = 0;
        for (int start = 0; start < content.length(); ) {
            int end = Math.min(content.length(), start + CHUNK_SIZE);
            String text = content.substring(start, end).trim();
            if (!text.isBlank()) {
                String chunkId = UUID.nameUUIDFromBytes((material.getId() + ":" + seq).getBytes(StandardCharsets.UTF_8)).toString();
                chunks.add(new MaterialChunk(chunkId, material.getId(), material.getTitle(), text, seq, material.getTagsJson()));
                seq++;
            }
            if (end >= content.length()) {
                break;
            }
            start = Math.max(end - OVERLAP, start + 1);
        }
        return chunks;
    }

    private String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').replaceAll("\\s+", " ").trim();
    }
}
