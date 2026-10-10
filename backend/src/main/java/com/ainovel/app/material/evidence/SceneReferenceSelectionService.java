package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import com.ainovel.app.user.User;

import org.springframework.stereotype.Service;

import java.util.*;

/** One frozen selection is reused by fast/crafted drafting and every length retry. */
@Service
public class SceneReferenceSelectionService {
    private final MaterialEvidenceService sources;
    private final MaterialSearchService search;

    public SceneReferenceSelectionService(
            MaterialEvidenceService sources, MaterialSearchService search) {
        this.sources = sources;
        this.search = search;
    }

    public List<Hit> select(User user, UUID story, UUID manuscript, UUID scene, String query) {
        var selected = sources.scenePackage(user, manuscript, scene.toString());
        var bindings = sources.settings(user, story).bindings();
        List<Hit> result = new ArrayList<>();
        for (String id : selected.pinned()) {
            var source = sources.open(user, id);
            if (!bindings.contains(source.materialId()))
                throw new IllegalStateException("PINNED_REFERENCE_STALE");
            result.add(source);
        }
        if (!query.isBlank())
            for (var h :
                    search.search(user, new Search(query, story, "fact", "bound", 40)).items()) {
                if (result.size() < 8
                        && !selected.excluded().contains(h.chunkId())
                        && result.stream().noneMatch(v -> v.chunkId().equals(h.chunkId())))
                    result.add(h);
            }
        return List.copyOf(result);
    }

    public void revalidate(User user, List<Hit> selected) {
        for (var hit : selected) sources.validateFrozenReference(user, hit);
    }
}
