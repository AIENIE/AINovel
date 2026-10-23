package com.ainovel.app.world;

import com.ainovel.app.world.dto.*;
import java.time.Instant;
import java.util.*;

public final class WorldDraftDtos {
    private WorldDraftDtos() {}
    public record Entry(UUID id, String type, String title, String summary, String content,
                        List<String> aliases, List<String> tags, String legacyModuleKey, String legacyFieldKey) {}
    public record Relation(UUID id, UUID sourceId, UUID targetId, String type, String description) {}
    public record Content(String name, String tagline, List<String> themes, String creativeIntent, String notes,
                          List<Entry> entries, List<Relation> relations, Map<String, Map<String,String>> modules,
                          String legacyModulesJson) {}
    public record Version(UUID id, UUID worldId, int sequenceNumber, String origin, String legacyVersion,
                          long draftRevision, String contentHash, String releaseNote, Instant createdAt, Content content) {}
    public record Draft(WorldDetailDto world, List<Entry> entries, List<Relation> relations, Version latestPublished,
                        boolean unpublishedChanges) {}
    public record Save(WorldUpdateRequest metadata, List<Entry> entries, List<Relation> relations, Long expectedRevision) {}
    public record Publish(Long expectedRevision, String releaseNote, String requestKey) {}
    public record Change(String kind, String id, String label, Object before, Object after) {}
    public record Diff(UUID fromVersionId, UUID toVersionId, Long draftRevision, List<Change> changes) {}
    public record EntryAi(Long expectedRevision, String instruction) {}
}
