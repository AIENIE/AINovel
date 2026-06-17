package com.ainovel.app.v2;

import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2EntityExtraction;
import com.ainovel.app.v2.model.V2KnowledgeGraphRelationship;
import com.ainovel.app.v2.model.V2LorebookEntry;
import com.ainovel.app.v2.repo.V2EntityExtractionRepository;
import com.ainovel.app.v2.repo.V2KnowledgeGraphRelationshipRepository;
import com.ainovel.app.v2.repo.V2LorebookEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class V2ContextPersistenceService {
    private final V2LorebookEntryRepository lorebookRepository;
    private final V2EntityExtractionRepository extractionRepository;
    private final V2KnowledgeGraphRelationshipRepository relationshipRepository;

    public V2ContextPersistenceService(V2LorebookEntryRepository lorebookRepository,
                                       V2EntityExtractionRepository extractionRepository,
                                       V2KnowledgeGraphRelationshipRepository relationshipRepository) {
        this.lorebookRepository = lorebookRepository;
        this.extractionRepository = extractionRepository;
        this.relationshipRepository = relationshipRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listLorebook(UUID storyId) {
        return lorebookRepository.findByStoryIdOrderByPriorityDesc(storyId).stream().map(this::entryMap).toList();
    }

    @Transactional
    public Map<String, Object> createLorebook(User user, Story story, Map<String, Object> payload) {
        V2LorebookEntry entry = new V2LorebookEntry();
        entry.setUser(user);
        entry.setStory(story);
        applyLorebookPayload(entry, payload);
        return entryMap(lorebookRepository.save(entry));
    }

    @Transactional
    public Map<String, Object> updateLorebook(UUID storyId, UUID entryId, Map<String, Object> payload) {
        V2LorebookEntry entry = requireLorebook(storyId, entryId);
        applyLorebookPayload(entry, payload);
        return entryMap(lorebookRepository.save(entry));
    }

    @Transactional
    public void deleteLorebook(UUID storyId, UUID entryId) {
        relationshipRepository.deleteByStoryIdAndSourceIdOrStoryIdAndTargetId(storyId, entryId, storyId, entryId);
        lorebookRepository.delete(requireLorebook(storyId, entryId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRelationships(UUID storyId) {
        return relationshipRepository.findByStoryId(storyId).stream().map(this::relationshipMap).toList();
    }

    @Transactional
    public Map<String, Object> createRelationship(Story story, UUID sourceId, UUID targetId, String relationType) {
        V2KnowledgeGraphRelationship relationship = new V2KnowledgeGraphRelationship();
        relationship.setStory(story);
        relationship.setSource(requireLorebook(story.getId(), sourceId));
        relationship.setTarget(requireLorebook(story.getId(), targetId));
        relationship.setRelationType(blank(relationType, "related_to"));
        return relationshipMap(relationshipRepository.save(relationship));
    }

    @Transactional
    public void deleteRelationship(UUID storyId, UUID relationshipId) {
        relationshipRepository.deleteByStoryIdAndId(storyId, relationshipId);
    }

    @Transactional
    public Map<String, Object> createExtraction(Story story, Map<String, Object> payload) {
        String text = blank(payload.get("text"), "");
        if (text.isBlank()) {
            throw new RuntimeException("text 不能为空");
        }
        V2EntityExtraction extraction = new V2EntityExtraction();
        extraction.setStory(story);
        extraction.setEntityName(blank(payload.get("entityName"), text.substring(0, Math.min(12, text.length()))));
        extraction.setEntityType(blank(payload.get("entityType"), "concept"));
        extraction.setAttributesJson(V2Json.write(payload.getOrDefault("attributes", Map.of("source", "ai"))));
        extraction.setSourceText(text.substring(0, Math.min(text.length(), 200)));
        extraction.setConfidence(doubleVal(payload.get("confidence"), 0.82d));
        extraction.setReviewed(false);
        extraction.setReviewAction("pending");
        return extractionMap(extractionRepository.save(extraction));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExtractions(UUID storyId) {
        return extractionRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream().map(this::extractionMap).toList();
    }

    @Transactional
    public Map<String, Object> reviewExtraction(UUID storyId, UUID extractionId, Map<String, Object> payload) {
        V2EntityExtraction extraction = extractionRepository.findByStoryIdAndId(storyId, extractionId)
                .orElseThrow(() -> new RuntimeException("提取记录不存在"));
        extraction.setReviewed(true);
        extraction.setReviewAction(blank(payload.get("reviewAction"), "approved"));
        if (payload.get("linkedLorebookId") != null) {
            extraction.setLinkedLorebook(requireLorebook(storyId, UUID.fromString(payload.get("linkedLorebookId").toString())));
        }
        return extractionMap(extractionRepository.save(extraction));
    }

    private void applyLorebookPayload(V2LorebookEntry entry, Map<String, Object> payload) {
        UUID fallbackId = entry.getId() == null ? UUID.randomUUID() : entry.getId();
        entry.setEntryKey(blank(payload.get("entryKey"), "entry-" + fallbackId.toString().substring(0, 8)));
        entry.setDisplayName(blank(payload.get("displayName"), "未命名条目"));
        entry.setCategory(blank(payload.get("category"), "custom"));
        entry.setContent(blank(payload.get("content"), ""));
        entry.setKeywordsJson(V2Json.write(list(payload.get("keywords"))));
        entry.setPriority(intVal(payload.get("priority"), 0));
        entry.setEnabled(boolVal(payload.get("enabled"), true));
        entry.setInsertionPosition(blank(payload.get("insertionPosition"), "before_scene"));
        entry.setTokenBudget(intVal(payload.get("tokenBudget"), 500));
    }

    private V2LorebookEntry requireLorebook(UUID storyId, UUID id) {
        return lorebookRepository.findByStoryIdAndId(storyId, id)
                .orElseThrow(() -> new RuntimeException("Lorebook 条目不存在"));
    }

    private Map<String, Object> entryMap(V2LorebookEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", entry.getId());
        out.put("storyId", entry.getStory().getId());
        out.put("userId", entry.getUser().getId());
        out.put("entryKey", entry.getEntryKey());
        out.put("displayName", entry.getDisplayName());
        out.put("category", entry.getCategory());
        out.put("content", entry.getContent());
        out.put("keywords", V2Json.list(entry.getKeywordsJson()));
        out.put("priority", entry.getPriority());
        out.put("enabled", entry.isEnabled());
        out.put("insertionPosition", entry.getInsertionPosition());
        out.put("tokenBudget", entry.getTokenBudget());
        out.put("createdAt", entry.getCreatedAt());
        out.put("updatedAt", entry.getUpdatedAt());
        return out;
    }

    private Map<String, Object> relationshipMap(V2KnowledgeGraphRelationship relationship) {
        return new LinkedHashMap<>(Map.of(
                "id", relationship.getId(),
                "storyId", relationship.getStory().getId(),
                "source", relationship.getSource().getId(),
                "target", relationship.getTarget().getId(),
                "relationType", relationship.getRelationType(),
                "createdAt", relationship.getCreatedAt() == null ? Instant.now() : relationship.getCreatedAt()
        ));
    }

    private Map<String, Object> extractionMap(V2EntityExtraction extraction) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", extraction.getId());
        out.put("storyId", extraction.getStory().getId());
        out.put("manuscriptId", extraction.getManuscript() == null ? null : extraction.getManuscript().getId());
        out.put("entityName", extraction.getEntityName());
        out.put("entityType", extraction.getEntityType());
        out.put("attributes", V2Json.map(extraction.getAttributesJson()));
        out.put("sourceText", extraction.getSourceText());
        out.put("confidence", extraction.getConfidence());
        out.put("reviewed", extraction.isReviewed());
        out.put("reviewAction", extraction.getReviewAction());
        out.put("linkedLorebookId", extraction.getLinkedLorebook() == null ? null : extraction.getLinkedLorebook().getId());
        out.put("createdAt", extraction.getCreatedAt());
        return out;
    }

    private static List<Object> list(Object value) {
        return value instanceof List<?> raw ? new ArrayList<>(raw) : new ArrayList<>();
    }

    private static String blank(Object value, String fallback) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        return text.isBlank() ? fallback : text;
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(value.toString()); } catch (Exception ex) { return fallback; }
    }

    private static double doubleVal(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(value.toString()); } catch (Exception ex) { return fallback; }
    }

    private static boolean boolVal(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : value == null ? fallback : Boolean.parseBoolean(value.toString());
    }
}
