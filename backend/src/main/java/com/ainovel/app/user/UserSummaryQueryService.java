package com.ainovel.app.user;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.dto.UserSummaryResponse;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserSummaryQueryService {
    private final StoryRepository storyRepository;
    private final WorldRepository worldRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final JsonColumnCodec jsonColumnCodec;

    public UserSummaryQueryService(StoryRepository storyRepository,
                                   WorldRepository worldRepository,
                                   ManuscriptRepository manuscriptRepository,
                                   JsonColumnCodec jsonColumnCodec) {
        this.storyRepository = storyRepository;
        this.worldRepository = worldRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.jsonColumnCodec = jsonColumnCodec;
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse summary(User user) {
        long novelCount = storyRepository.countByUser(user);
        long worldCount = worldRepository.countByUser(user);
        long totalWords = estimateTotalWords(user);
        long totalEntries = estimateWorldEntries(user);
        return new UserSummaryResponse(novelCount, worldCount, totalWords, totalEntries);
    }

    private long estimateTotalWords(User user) {
        long total = 0;
        for (Manuscript manuscript : manuscriptRepository.findByStoryUser(user)) {
            total += estimateWordsFromSections(manuscript.getSectionsJson());
        }
        return total;
    }

    private long estimateWordsFromSections(String sectionsJson) {
        Map<String, String> sections = jsonColumnCodec.read(sectionsJson, new TypeReference<>() {}, Map.of());
        long total = 0;
        for (String html : sections.values()) {
            if (html == null) {
                continue;
            }
            String plain = html.replaceAll("<[^>]*>", "");
            total += plain.trim().length();
        }
        return total;
    }

    private long estimateWorldEntries(User user) {
        long total = 0;
        for (World world : worldRepository.findByUser(user)) {
            total += countNonEmptyEntries(world.getModulesJson());
        }
        return total;
    }

    private long countNonEmptyEntries(String modulesJson) {
        Map<String, Map<String, String>> modules = jsonColumnCodec.read(modulesJson, new TypeReference<>() {}, Map.of());
        long total = 0;
        for (Map<String, String> fields : modules.values()) {
            if (fields == null) {
                continue;
            }
            for (String value : fields.values()) {
                if (value != null && !value.isBlank()) {
                    total++;
                }
            }
        }
        return total;
    }
}
