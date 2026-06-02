package com.ainovel.app.material;

import com.ainovel.app.material.dto.MaterialSearchRequest;
import com.ainovel.app.material.dto.MaterialSearchResultDto;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MaterialRetrievalService {
    private final MaterialRepository materialRepository;
    private final MaterialChunker chunker;
    private final TextEmbeddingClient embeddingClient;
    private final MaterialVectorIndex vectorIndex;

    public MaterialRetrievalService(MaterialRepository materialRepository,
                                    MaterialChunker chunker,
                                    TextEmbeddingClient embeddingClient,
                                    MaterialVectorIndex vectorIndex) {
        this.materialRepository = materialRepository;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
    }

    public void indexMaterial(User user, Material material) {
        if (!isApproved(material)) {
            return;
        }
        for (MaterialChunk chunk : chunker.chunks(material)) {
            try {
                float[] vector = embeddingClient.embed(user, chunk.text());
                if (vector != null && vector.length > 0) {
                    vectorIndex.upsert(chunk, vector);
                }
            } catch (RuntimeException ignored) {
                return;
            }
        }
    }

    public List<MaterialSearchResultDto> search(User user, MaterialSearchRequest request) {
        String query = request == null || request.query() == null ? "" : request.query().trim();
        int limit = Math.max(1, Math.min(request != null && request.limit() != null ? request.limit() : 10, 30));
        List<Material> visible = visibleMaterials(user);

        Map<String, MaterialSearchResultDto> merged = new LinkedHashMap<>();
        for (MaterialSearchResultDto result : keywordSearch(visible, query, limit * 2)) {
            merged.put(result.chunkId(), result);
        }

        try {
            float[] queryVector = embeddingClient.embed(user, query);
            if (queryVector != null && queryVector.length > 0) {
                Map<String, MaterialChunk> chunkById = chunksById(visible);
                for (VectorMatch match : vectorIndex.search(queryVector, limit * 2)) {
                    MaterialChunk chunk = chunkById.get(match.chunkId());
                    if (chunk == null) {
                        continue;
                    }
                    MaterialSearchResultDto vectorResult = toResult(chunk, match.score(), "vector", List.of("semantic"));
                    merged.merge(vectorResult.chunkId(), vectorResult, this::higherScore);
                }
            }
        } catch (RuntimeException ignored) {
            // Keyword results remain the fallback path.
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(MaterialSearchResultDto::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<MaterialSearchResultDto> keywordSearch(List<Material> materials, String query, int limit) {
        List<String> terms = terms(query);
        List<MaterialSearchResultDto> results = new ArrayList<>();
        for (Material material : materials) {
            for (MaterialChunk chunk : chunker.chunks(material)) {
                Score score = keywordScore(material, chunk, terms);
                if (score.value() <= 0) {
                    continue;
                }
                results.add(toResult(chunk, score.value(), "keyword", score.reasons()));
            }
        }
        return results.stream()
                .sorted(Comparator.comparingDouble(MaterialSearchResultDto::score).reversed())
                .limit(limit)
                .toList();
    }

    private Score keywordScore(Material material, MaterialChunk chunk, List<String> terms) {
        if (terms.isEmpty()) {
            return new Score(0, List.of());
        }
        String title = lower(material.getTitle());
        String tags = lower(material.getTagsJson());
        String summary = lower(material.getSummary());
        String content = lower(chunk.text());
        double score = 0;
        Set<String> reasons = new LinkedHashSet<>();
        for (String term : terms) {
            if (title.contains(term)) {
                score += 3;
                reasons.add("title");
            }
            if (tags.contains(term)) {
                score += 2.2;
                reasons.add("tags");
            }
            if (summary.contains(term)) {
                score += 1.6;
                reasons.add("summary");
            }
            if (content.contains(term)) {
                score += 1.2;
                reasons.add("content");
            }
        }
        return new Score(score, List.copyOf(reasons));
    }

    private MaterialSearchResultDto toResult(MaterialChunk chunk, double score, String source, List<String> reasons) {
        return new MaterialSearchResultDto(
                chunk.materialId(),
                chunk.chunkId(),
                chunk.title(),
                snippet(chunk.text()),
                score,
                chunk.chunkSeq(),
                source,
                reasons == null ? List.of() : reasons
        );
    }

    private MaterialSearchResultDto higherScore(MaterialSearchResultDto left, MaterialSearchResultDto right) {
        return left.score() >= right.score() ? left : right;
    }

    private List<Material> visibleMaterials(User user) {
        return materialRepository.findAll().stream()
                .filter(this::isApproved)
                .filter(material -> user == null
                        || material.getUser() == null
                        || user.getUsername().equals(material.getUser().getUsername()))
                .toList();
    }

    private boolean isApproved(Material material) {
        return material != null && "approved".equalsIgnoreCase(material.getStatus());
    }

    private Map<String, MaterialChunk> chunksById(List<Material> materials) {
        Map<String, MaterialChunk> chunks = new LinkedHashMap<>();
        for (Material material : materials) {
            for (MaterialChunk chunk : chunker.chunks(material)) {
                chunks.put(chunk.chunkId(), chunk);
            }
        }
        return chunks;
    }

    private List<String> terms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = lower(query).replaceAll("[^\\p{IsHan}a-z0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] raw = normalized.split("\\s+");
        List<String> terms = new ArrayList<>();
        for (String term : raw) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        return terms;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 180 ? text : text.substring(0, 180) + "...";
    }

    private record Score(double value, List<String> reasons) {
    }
}
