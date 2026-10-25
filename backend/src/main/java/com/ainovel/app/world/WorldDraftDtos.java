package com.ainovel.app.world;

import com.ainovel.app.world.dto.*;
import java.time.Instant;
import java.util.*;

public final class WorldDraftDtos {
    private WorldDraftDtos() {}
    public record Entry(UUID id, String type, String title, String summary, String content,
                        List<String> aliases, List<String> tags, String legacyModuleKey, String legacyFieldKey) {}
    public record Relation(UUID id, UUID sourceId, UUID targetId, String type, String description) {}
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=WorldDatePointDeserializer.class)
    public record DatePoint(Integer year, Integer month, Integer day) {}
    public record DateRange(DatePoint start, DatePoint end) {}
    public record Era(UUID id, String name, String description, DateRange date, int sortOrder) {}
    public record Event(UUID id, String title, String summary, String content, List<String> aliases,
                        List<String> tags, DateRange date, List<UUID> entryIds, List<UUID> eraIds, int sortOrder) {}
    public record Timeline(int schemaVersion, String calendarName, List<Era> eras, List<Event> events) {}
    public record Content(String name, String tagline, List<String> themes, String creativeIntent, String notes,
                          List<Entry> entries, List<Relation> relations, Map<String, Map<String,String>> modules,
                          String legacyModulesJson,
                          @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) String overview,
                          @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Timeline timeline) {
        public Content { timeline=WorldTimeline.normalize(timeline); }
        public Content(String name,String tagline,List<String> themes,String creativeIntent,String notes,List<Entry> entries,List<Relation> relations,Map<String,Map<String,String>> modules,String legacyModulesJson) {
            this(name,tagline,themes,creativeIntent,notes,entries,relations,modules,legacyModulesJson,null,null);
        }
        public Content(String name,String tagline,List<String> themes,String creativeIntent,String notes,List<Entry> entries,List<Relation> relations,Map<String,Map<String,String>> modules,String legacyModulesJson,String overview) {
            this(name,tagline,themes,creativeIntent,notes,entries,relations,modules,legacyModulesJson,overview,null);
        }
    }
    public record Version(UUID id, UUID worldId, int sequenceNumber, String origin, String legacyVersion,
                          long draftRevision, String contentHash, String releaseNote, Instant createdAt, Content content) {}
    public record Draft(WorldDetailDto world, List<Entry> entries, List<Relation> relations, Version latestPublished,
                        boolean unpublishedChanges, Timeline timeline) {
        public Draft(WorldDetailDto world,List<Entry> entries,List<Relation> relations,Version latestPublished,boolean unpublishedChanges) {
            this(world,entries,relations,latestPublished,unpublishedChanges,null);
        }
    }
    public record Save(WorldUpdateRequest metadata, List<Entry> entries, List<Relation> relations, Long expectedRevision, Timeline timeline) {
        public Save(WorldUpdateRequest metadata,List<Entry> entries,List<Relation> relations,Long expectedRevision) {
            this(metadata,entries,relations,expectedRevision,null);
        }
    }
    public record Publish(Long expectedRevision, String releaseNote, String requestKey) {}
    public record Change(String kind, String id, String label, Object before, Object after) {}
    public record Diff(UUID fromVersionId, UUID toVersionId, Long draftRevision, List<Change> changes) {}
    public record EntryAi(Long expectedRevision, String instruction) {}
}
