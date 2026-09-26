package com.ainovel.app.narrative;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import com.ainovel.app.narrative.NarrativeDtos.Evidence;

/** H2 author-owned configuration. No machine result is an accepted knowledge grant. */
public final class NarrativeContextDtos {
    private NarrativeContextDtos() {}
    public enum Perspective { FIRST_PERSON, LIMITED_THIRD, OMNISCIENT }
    public enum View { CHARACTER, READER, SCENE }
    public enum EntryKind { BACKGROUND, PLAN, READER_HYPOTHESIS }
    public record Policy(@NotNull Perspective perspective, boolean allowInner,
                         @NotNull Map<UUID, UUID> viewpointByScene) {}
    public record Grant(@NotNull UUID id, @NotNull UUID recordId, @NotNull UUID characterId,
                        @NotNull UUID approvalId, @NotEmpty List<@Valid Evidence> evidence,
                        @NotNull UUID fromSceneId, @Size(max=500) String uncertainty, boolean stale,
                        @Valid NarrativeDtos.KnowledgeView view) {
        public Grant(UUID id, UUID recordId, UUID characterId, UUID approvalId, List<Evidence> evidence,
                     UUID fromSceneId, String uncertainty, boolean stale) {
            this(id,recordId,characterId,approvalId,evidence,fromSceneId,uncertainty,stale,null);
        }
    }
    public record Entry(@NotNull UUID id, @NotNull EntryKind kind, @NotBlank @Size(max=2000) String text,
                        @NotNull UUID fromSceneId, @NotNull List<UUID> characterIds,
                        boolean narratorVisible, @NotNull List<UUID> dependencyRecordIds, boolean stale) {}
    public record Document(@Valid @NotNull Policy policy, @NotNull @Size(max=300) List<@Valid Grant> grants,
                           @NotNull @Size(max=300) List<@Valid Entry> entries, Map<UUID,String> positionHashes) {
        public Document(Policy policy,List<Grant> grants,List<Entry> entries) { this(policy,grants,entries,Map.of()); }
        public Document { positionHashes=positionHashes==null?Map.of():Map.copyOf(positionHashes); }
    }
    public record State(boolean enabled, long settingsRevision, long revision, long manuscriptVersion,
                        long canonRevision, Document document) {}
    public record Update(@NotNull Long expectedManuscriptVersion, @NotNull Long expectedCanonRevision,
                         @NotNull Long expectedRevision, @NotNull Long expectedSettingsRevision,
                         boolean enabled, @Valid @NotNull Document document) {}
    public record Stamp(UUID manuscriptId, UUID branchId, long manuscriptVersion, long canonRevision,
                        long contextRevision, long settingsRevision, String orderHash) {}
    public record Fragment(String id, String category, String content, String reason, boolean truncated) {}
    public record Preview(View view, UUID characterId, UUID sceneId, Stamp stamp, String contextHash,
                          int tokenBudget, int tokenUsed, String content, List<Fragment> included,
                          List<Fragment> excluded) {
        @com.fasterxml.jackson.annotation.JsonProperty("promptVersion")
        public String promptVersion() { return NarrativeContextService.VERSION; }
    }
}
