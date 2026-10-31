package com.ainovel.app.worldcontext;

import com.ainovel.app.world.WorldBindingService;
import java.util.*;

public final class WorldContextDtos {
    private WorldContextDtos() {}
    public enum Choice { AUTO, INCLUDE, EXCLUDE }
    public record Preferences(UUID worldId, long revision, UUID reviewedVersionId,
            Map<String,Choice> choices, Map<String,String> reviewedHashes) {
        public Preferences {
            choices=choices==null?Map.of():Collections.unmodifiableMap(new TreeMap<>(choices));
            reviewedHashes=reviewedHashes==null?Map.of():Collections.unmodifiableMap(new TreeMap<>(reviewedHashes));
        }
        public static Preferences defaults(UUID world) { return new Preferences(world,0,null,Map.of(),Map.of()); }
    }
    public record Save(UUID worldId, UUID versionId, Long expectedRevision, Map<String,Choice> choices) {}
    public record Source(String id, String type, String title, String content, String contentHash,
            Choice choice, String reason, int estimatedTokens, boolean required) {}
    public record Selection(String compilerVersion, WorldBindingService.Resolved binding, long selectionRevision,
            String queryHash, String contextHash, int tokenBudget, int tokenUsed, String content,
            List<Source> included, List<Source> excluded, List<String> issues) {}
}
