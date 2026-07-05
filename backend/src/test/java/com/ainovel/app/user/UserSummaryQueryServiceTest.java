package com.ainovel.app.user;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.dto.UserSummaryResponse;
import com.ainovel.app.world.model.World;
import com.ainovel.app.world.repo.WorldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSummaryQueryServiceTest {

    @Test
    void summaryShouldAggregateCountsAndContentFromSingleLevelQueries() {
        StoryRepository storyRepository = mock(StoryRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        JsonColumnCodec jsonColumnCodec = new JsonColumnCodec(new ObjectMapper());

        User user = new User();
        when(storyRepository.countByUser(user)).thenReturn(3L);
        when(worldRepository.countByUser(user)).thenReturn(2L);
        when(manuscriptRepository.findByStoryUser(user)).thenReturn(List.of(
                manuscript("{\"scene-1\":\"<p>abc</p>\",\"scene-2\":\"  hi  \"}"),
                manuscript("{\"scene-3\":\"<div>1234</div>\"}")
        ));
        when(worldRepository.findByUser(user)).thenReturn(List.of(
                world("{\"region\":{\"climate\":\"cold\",\"note\":\"\"},\"history\":{\"era\":\" dawn \"}}"),
                world("{\"characters\":{\"lead\":\"hero\",\"ally\":\"   \",\"mentor\":null}}")
        ));

        UserSummaryQueryService service = new UserSummaryQueryService(
                storyRepository,
                worldRepository,
                manuscriptRepository,
                jsonColumnCodec
        );

        UserSummaryResponse result = service.summary(user);

        assertEquals(3L, result.novelCount());
        assertEquals(2L, result.worldCount());
        assertEquals(9L, result.totalWords());
        assertEquals(3L, result.totalEntries());
    }

    @Test
    void summaryShouldDeclareReadOnlyTransactionBoundary() throws Exception {
        Method method = UserSummaryQueryService.class.getMethod("summary", User.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertTrue(transactional != null && transactional.readOnly());
    }

    private Manuscript manuscript(String sectionsJson) {
        Manuscript manuscript = new Manuscript();
        manuscript.setSectionsJson(sectionsJson);
        return manuscript;
    }

    private World world(String modulesJson) {
        World world = new World();
        world.setModulesJson(modulesJson);
        return world;
    }
}
