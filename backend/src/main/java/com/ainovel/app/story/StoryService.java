package com.ainovel.app.story;

import com.ainovel.app.common.RefineRequest;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.story.dto.*;
import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.CharacterCardRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiRefineRequest;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Service
public class StoryService {
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private CharacterCardRepository characterCardRepository;
    @Autowired
    private AiService aiService;
    @Autowired
    private ResourceAccessGuard accessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<StoryDto> listStories(User user) {
        accessGuard.assertCurrentUserEquals(user.getUsername());
        return storyRepository.findByUser(user).stream().map(this::toDto).toList();
    }

    public StoryDto getStory(UUID id) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        return toDto(story);
    }

    @Transactional
    public StoryDto createStory(User user, StoryCreateRequest request) {
        Story story = new Story();
        story.setUser(user);
        story.setTitle(request.title());
        story.setSynopsis(request.synopsis());
        story.setGenre(request.genre());
        story.setTone(request.tone());
        story.setWorldId(request.worldId());
        story.setStatus("draft");
        storyRepository.save(story);
        return toDto(story);
    }

    @Transactional
    public StoryDto updateStory(UUID id, StoryUpdateRequest request) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        if (request.title() != null) story.setTitle(request.title());
        if (request.synopsis() != null) story.setSynopsis(request.synopsis());
        if (request.genre() != null) story.setGenre(request.genre());
        if (request.tone() != null) story.setTone(request.tone());
        if (request.status() != null) story.setStatus(request.status());
        if (request.worldId() != null) story.setWorldId(request.worldId());
        storyRepository.save(story);
        return toDto(story);
    }

    @Transactional
    public void deleteStory(UUID id) {
        Story story = storyRepository.findByIdWithUser(id).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        characterCardRepository.deleteAll(characterCardRepository.findByStory(story));
        storyRepository.delete(story);
    }

    public List<CharacterDto> listCharacters(UUID storyId) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow();
        accessGuard.assertOwner(story.getUser());
        return characterCardRepository.findByStory(story).stream().map(this::toCharacterDto).toList();
    }

    @Transactional
    public CharacterDto addCharacter(UUID storyId, CharacterRequest request) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow();
        accessGuard.assertOwner(story.getUser());
        CharacterCard card = new CharacterCard();
        card.setStory(story);
        card.setName(request.name());
        card.setSynopsis(request.synopsis());
        card.setDetails(request.details());
        card.setRelationships(request.relationships());
        characterCardRepository.save(card);
        return toCharacterDto(card);
    }

    @Transactional
    public CharacterDto updateCharacter(UUID id, CharacterRequest request) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        if (request.name() != null) card.setName(request.name());
        card.setSynopsis(request.synopsis());
        card.setDetails(request.details());
        card.setRelationships(request.relationships());
        characterCardRepository.save(card);
        return toCharacterDto(card);
    }

    @Transactional
    public void deleteCharacter(UUID id) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        characterCardRepository.delete(card);
    }

    public String refineStory(User user, UUID storyId, RefineRequest request) {
        Story story = storyRepository.findByIdWithUser(storyId).orElseThrow(() -> new RuntimeException("故事不存在"));
        accessGuard.assertOwner(story.getUser());
        String instruction = request.instruction() == null ? "" : request.instruction();
        return aiService.refine(user, new AiRefineRequest(request.text(), instruction, null)).result();
    }

    public String refineCharacter(User user, UUID characterId, RefineRequest request) {
        CharacterCard card = characterCardRepository.findByIdWithStoryUser(characterId).orElseThrow(() -> new RuntimeException("角色不存在"));
        accessGuard.assertOwner(card.getStory().getUser());
        String instruction = request.instruction() == null ? "" : request.instruction();
        return aiService.refine(user, new AiRefineRequest(request.text(), instruction, null)).result();
    }

    @Transactional
    public Map<String, Object> conception(User user, StoryCreateRequest request) {
        StoryDto story = createStory(user, request);

        Map<String, Object> generated = generateConceptionDraft(user, request);
        StoryDto updatedStory = story;
        if (!generated.isEmpty()) {
            updatedStory = updateStory(story.id(), new StoryUpdateRequest(
                    asText(generated.get("title"), story.title()),
                    asText(generated.get("synopsis"), request.synopsis()),
                    asText(generated.get("genre"), request.genre()),
                    asText(generated.get("tone"), request.tone()),
                    null,
                    request.worldId()
            ));
        }

        List<CharacterDto> createdCharacters = new ArrayList<>();
        List<Map<String, Object>> generatedCharacters = readObjectList(generated.get("characters"));
        if (!generatedCharacters.isEmpty()) {
            for (Map<String, Object> item : generatedCharacters) {
                String name = asText(item.get("name"), "");
                if (name.isBlank()) continue;
                createdCharacters.add(addCharacter(updatedStory.id(),
                        new CharacterRequest(
                                name,
                                asText(item.get("synopsis"), "AI 生成角色设定"),
                                asText(item.get("details"), ""),
                                asText(item.get("relationships"), "")
                        )));
            }
        }
        if (createdCharacters.isEmpty()) {
            createdCharacters.add(addCharacter(updatedStory.id(),
                    new CharacterRequest("主角", "AI 生成的主角设定", "初始详情", "关系网")));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("storyCard", updatedStory);
        result.put("characterCards", createdCharacters);
        result.put("generated", generated);
        return result;
    }

    private Map<String, Object> generateConceptionDraft(User user, StoryCreateRequest request) {
        try {
            String prompt = """
                    请根据用户输入生成小说设定草案，必须返回 JSON（不要 markdown），结构：
                    {
                      "title":"故事标题",
                      "synopsis":"120-200字故事梗概",
                      "genre":"类型",
                      "tone":"风格基调",
                      "characters":[
                        {"name":"角色名","synopsis":"一句话定位","details":"关键背景","relationships":"与其他角色关系"}
                      ]
                    }
                    用户输入：
                    标题：%s
                    梗概：%s
                    类型：%s
                    基调：%s
                    """.formatted(
                    safe(request.title()),
                    safe(request.synopsis()),
                    safe(request.genre()),
                    safe(request.tone())
            );
            var response = aiService.chat(user, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", prompt)),
                    null,
                    null
            ));
            Map<String, Object> parsed = parseJsonObject(response.content());
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> parseJsonObject(String text) {
        if (text == null || text.isBlank()) return null;
        String candidate = text.trim();
        if (candidate.startsWith("```")) {
            int first = candidate.indexOf('{');
            int last = candidate.lastIndexOf('}');
            if (first >= 0 && last > first) {
                candidate = candidate.substring(first, last + 1);
            }
        }
        try {
            return objectMapper.readValue(candidate, new TypeReference<>() {});
        } catch (Exception ignored) {
            int first = candidate.indexOf('{');
            int last = candidate.lastIndexOf('}');
            if (first >= 0 && last > first) {
                try {
                    return objectMapper.readValue(candidate.substring(first, last + 1), new TypeReference<>() {});
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private List<Map<String, Object>> readObjectList(Object value) {
        if (!(value instanceof List<?> rawList)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                out.add(copy);
            }
        }
        return out;
    }

    private String asText(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private StoryDto toDto(Story story) {
        return new StoryDto(story.getId(), story.getTitle(), story.getSynopsis(), story.getGenre(), story.getTone(), story.getStatus(), story.getWorldId(), story.getUpdatedAt());
    }

    private CharacterDto toCharacterDto(CharacterCard card) {
        return new CharacterDto(card.getId(), card.getName(), card.getSynopsis(), card.getDetails(), card.getRelationships(), card.getUpdatedAt());
    }
}
