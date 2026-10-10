package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import com.ainovel.app.user.User;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MaterialSearchService {
    private final MaterialEvidenceService sources;
    private final MaterialSemanticService semantic;
    private final MaterialNativeClient client;

    public MaterialSearchService(
            MaterialEvidenceService sources,
            MaterialSemanticService semantic,
            MaterialNativeClient client) {
        this.sources = sources;
        this.semantic = semantic;
        this.client = client;
    }

    public Results search(User user, Search query) {
        var recall =
                sources.basicRecall(
                        user,
                        new Search(
                                query.query(), query.storyId(), query.mode(), query.scope(), 40));
        var settings = sources.settings(user, query.storyId());
        List<Hit> candidates =
                MaterialEvidenceService.fuse(recall.rankings()).stream().limit(40).toList();
        List<String> degraded = new ArrayList<>();
        String intent = "search:" + UUID.randomUUID();
        if (!settings.semanticProfile().equals("basic")) {
            if (!semantic.enabled()) degraded.add("语义检索尚未启用，使用基础检索");
            else
                try {
                    var allowed = sources.visibleMaterialIds(user, query.storyId(), recall.scope());
                    var matches =
                            semantic.search(
                                    user,
                                    intent + ":embed",
                                    settings.semanticProfile(),
                                    query.query(),
                                    allowed);
                    var current =
                            sources.visibleHits(
                                    user,
                                    query,
                                    matches.stream()
                                            .map(com.ainovel.app.material.VectorMatch::chunkId)
                                            .toList());
                    var byId = new HashMap<String, Hit>();
                    current.forEach(h -> byId.put(h.chunkId(), h));
                    var ranked =
                            matches.stream()
                                    .map(m -> byId.get(m.chunkId()))
                                    .filter(Objects::nonNull)
                                    .toList();
                    candidates =
                            MaterialEvidenceService.fuse(
                                            List.of(recall.exact(), recall.full(), ranked))
                                    .stream()
                                    .limit(40)
                                    .toList();
                } catch (RuntimeException failure) {
                    degraded.add("语义处理不可用，已保留基础检索结果");
                }
        }
        if (settings.rerank() && !candidates.isEmpty()) {
            if (!semantic.enabled()) degraded.add("重排尚未启用");
            else
                try {
                    candidates = recheck(user, query, candidates);
                    var response =
                            client.rerank(
                                    user,
                                    intent + ":rerank",
                                    query.query(),
                                    candidates.stream().map(Hit::text).toList(),
                                    recall.mode(),
                                    candidates.size());
                    List<Hit> ordered = new ArrayList<>();
                    Set<Integer> seen = new HashSet<>();
                    for (var result : response.getResultsList()) {
                        int i = result.getIndex();
                        if (i < 0
                                || i >= candidates.size()
                                || !seen.add(i)
                                || !Double.isFinite(result.getRelevanceScore()))
                            throw new IllegalStateException("RERANK_CONTRACT_INVALID");
                        var h = candidates.get(i);
                        var reasons = new ArrayList<>(h.reasons());
                        reasons.add("相关性重排");
                        ordered.add(
                                new Hit(
                                        h.chunkId(),
                                        h.materialId(),
                                        h.revisionId(),
                                        h.sourceVersion(),
                                        h.title(),
                                        h.text(),
                                        h.start(),
                                        h.end(),
                                        reasons,
                                        h.rank()));
                    }
                    if (ordered.size() != candidates.size())
                        throw new IllegalStateException("RERANK_COUNT_INVALID");
                    candidates = ordered;
                } catch (RuntimeException failure) {
                    degraded.add("重排不可用，已保留召回顺序");
                }
        }
        return new Results(
                recall.mode(),
                recall.scope(),
                recheck(user, query, candidates).stream()
                        .limit(query.limit() == null ? 8 : Math.min(40, query.limit()))
                        .toList(),
                degraded);
    }

    private List<Hit> recheck(User user, Search query, List<Hit> candidates) {
        var visible =
                sources.visibleHits(user, query, candidates.stream().map(Hit::chunkId).toList());
        Set<String> ids = new HashSet<>();
        visible.forEach(h -> ids.add(h.chunkId()));
        return candidates.stream().filter(h -> ids.contains(h.chunkId())).toList();
    }
}
