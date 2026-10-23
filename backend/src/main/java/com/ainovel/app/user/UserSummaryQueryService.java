package com.ainovel.app.user;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.dto.UserSummaryResponse;
import com.ainovel.app.world.repo.WorldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class UserSummaryQueryService {
    @org.springframework.beans.factory.annotation.Autowired private com.ainovel.app.manuscript.ManuscriptContentService contents;
    private final StoryRepository storyRepository;
    private final WorldRepository worldRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final com.ainovel.app.world.repo.WorldEntryRepository entries;

    public UserSummaryQueryService(StoryRepository storyRepository,
                                   WorldRepository worldRepository,
                                   ManuscriptRepository manuscriptRepository,
                                   com.ainovel.app.world.repo.WorldEntryRepository entries) {
        this.storyRepository = storyRepository;
        this.worldRepository = worldRepository;
        this.manuscriptRepository = manuscriptRepository;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse summary(User user) {
        long novelCount = storyRepository.countByUser(user);
        long worldCount = worldRepository.countByUser(user);
        long totalWords = estimateTotalWords(user);
        long totalEntries = entries.countFilledByUser(user.getId());
        return new UserSummaryResponse(novelCount, worldCount, totalWords, totalEntries);
    }

    private long estimateTotalWords(User user) {
        long total = 0;
        for (Manuscript manuscript : manuscriptRepository.findByStoryUser(user)) {
            total += contents.wordCount(manuscript);
        }
        return total;
    }

}
