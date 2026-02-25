package com.ainovel.app.manuscript;

import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.manuscript.dto.*;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.story.repo.OutlineRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ManuscriptService {
    @Autowired
    private ManuscriptRepository manuscriptRepository;
    @Autowired
    private OutlineRepository outlineRepository;
    @Autowired
    private ResourceAccessGuard accessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ManuscriptDto> listByOutline(UUID outlineId) {
        Outline outline = outlineRepository.findById(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        return manuscriptRepository.findByOutline(outline).stream().map(this::toDto).toList();
    }

    @Transactional
    public ManuscriptDto create(UUID outlineId, ManuscriptCreateRequest request) {
        Outline outline = outlineRepository.findById(outlineId).orElseThrow(() -> new RuntimeException("大纲不存在"));
        accessGuard.assertOwner(outline.getStory().getUser());
        Manuscript manuscript = new Manuscript();
        manuscript.setOutline(outline);
        manuscript.setTitle(request.title());
        manuscript.setWorldId(request.worldId());
        manuscript.setSectionsJson(writeJson(new HashMap<String, String>()));
        manuscript.setCharacterLogsJson(writeJson(new ArrayList<>()));
        manuscriptRepository.save(manuscript);
        return toDto(manuscript);
    }

    public ManuscriptDto get(UUID id) {
        Manuscript manuscript = manuscriptRepository.findById(id).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        return toDto(manuscript);
    }

    @Transactional
    public void delete(UUID id) {
        Manuscript manuscript = manuscriptRepository.findById(id).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        manuscriptRepository.delete(manuscript);
    }

    @Transactional
    public ManuscriptDto generateForScene(UUID manuscriptId, UUID sceneId) {
        Manuscript manuscript = manuscriptRepository.findById(manuscriptId).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        sections.put(sceneId.toString(), "自动生成的场景正文: " + sceneId);
        manuscript.setSectionsJson(writeJson(sections));
        manuscriptRepository.save(manuscript);
        return toDto(manuscript);
    }

    @Transactional
    public ManuscriptDto updateSection(UUID manuscriptId, UUID sceneId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findById(manuscriptId).orElseThrow(() -> new RuntimeException("稿件不存在"));
        accessGuard.assertOwner(ownerOf(manuscript));
        Map<String, String> sections = readSectionMap(manuscript.getSectionsJson());
        sections.put(sceneId.toString(), request.content());
        manuscript.setSectionsJson(writeJson(sections));
        manuscriptRepository.save(manuscript);
        return toDto(manuscript);
    }

    @Transactional
    public ManuscriptDto generateForScene(UUID sceneId) {
        Manuscript manuscript = manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("请先创建稿件"));
        return generateForScene(manuscript.getId(), sceneId);
    }

    @Transactional
    public ManuscriptDto updateSection(UUID sectionId, SectionUpdateRequest request) {
        Manuscript manuscript = manuscriptRepository.findAll().stream()
                .filter(m -> isCurrentUserOwner(ownerOf(m)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("请先创建稿件"));
        return updateSection(manuscript.getId(), sectionId, request);
    }

    @Transactional
    public List<CharacterChangeLogDto> analyzeCharacterChanges(UUID manuscriptId, AnalyzeCharacterChangeRequest request) {
        Manuscript manuscript = manuscriptRepository.findById(manuscriptId).orElseThrow();
        accessGuard.assertOwner(ownerOf(manuscript));
        List<Map<String, Object>> logs = readLogs(manuscript.getCharacterLogsJson());
        UUID logId = UUID.randomUUID();
        Map<String, Object> item = new HashMap<>();
        item.put("id", logId.toString());
        item.put("characterId", request.characterIds() != null && !request.characterIds().isEmpty() ? request.characterIds().get(0) : "char");
        item.put("summary", "检测到角色变化：" + (request.sectionContent() == null ? "" : request.sectionContent().substring(0, Math.min(20, request.sectionContent().length()))));
        item.put("createdAt", Instant.now().toString());
        logs.add(item);
        manuscript.setCharacterLogsJson(writeJson(logs));
        manuscriptRepository.save(manuscript);
        return mapLogs(logs);
    }

    public List<CharacterChangeLogDto> listCharacterLogs(UUID manuscriptId) {
        Manuscript manuscript = manuscriptRepository.findById(manuscriptId).orElseThrow();
        accessGuard.assertOwner(ownerOf(manuscript));
        return mapLogs(readLogs(manuscript.getCharacterLogsJson()));
    }

    public List<CharacterChangeLogDto> listCharacterLogs(UUID manuscriptId, UUID characterId) {
        return listCharacterLogs(manuscriptId).stream()
                .filter(l -> l.characterId().equals(characterId))
                .toList();
    }

    public String generateDialogue(RefineRequest request) {
        return "【记忆对话】" + request.text();
    }

    private ManuscriptDto toDto(Manuscript manuscript) {
        return new ManuscriptDto(
                manuscript.getId(),
                manuscript.getOutline().getId(),
                manuscript.getTitle(),
                manuscript.getWorldId(),
                readSectionMap(manuscript.getSectionsJson()),
                manuscript.getUpdatedAt()
        );
    }

    private Map<String, String> readSectionMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<Map<String, Object>> readLogs(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<CharacterChangeLogDto> mapLogs(List<Map<String, Object>> logs) {
        List<CharacterChangeLogDto> result = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            UUID id = UUID.fromString(log.get("id").toString());
            UUID characterId = UUID.fromString(log.get("characterId").toString());
            String summary = log.get("summary").toString();
            Instant createdAt = Instant.parse(log.get("createdAt").toString());
            result.add(new CharacterChangeLogDto(id, characterId, summary, createdAt));
        }
        return result;
    }

    private String writeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj);} catch (Exception e) { return "{}";}
    }

    private User ownerOf(Manuscript manuscript) {
        return manuscript.getOutline().getStory().getUser();
    }

    private boolean isCurrentUserOwner(User user) {
        try {
            accessGuard.assertOwner(user);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
