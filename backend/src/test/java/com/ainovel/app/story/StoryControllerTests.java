package com.ainovel.app.story;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.story.dto.OutlineCreateRequest;
import com.ainovel.app.story.dto.OutlineDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryControllerTests {

    @Test
    void listOutlinesShouldDelegateUsingStoryId() {
        StoryService storyService = mock(StoryService.class);
        OutlineService outlineService = mock(OutlineService.class);
        AiService aiService = mock(AiService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        StoryController controller = new StoryController(storyService, outlineService, aiService, currentUserResolver);
        UUID storyId = UUID.randomUUID();
        List<OutlineDto> outlines = List.of(new OutlineDto(
                UUID.randomUUID(),
                storyId,
                "outline",
                "world-1",
                Map.of(),
                List.of(),
                Instant.now()
        ));
        when(outlineService.listByStoryId(storyId)).thenReturn(outlines);

        List<OutlineDto> result = controller.listOutlines(storyId);

        assertEquals(outlines, result);
        verify(outlineService).listByStoryId(storyId);
    }

    @Test
    void createOutlineShouldDelegateUsingStoryId() {
        StoryService storyService = mock(StoryService.class);
        OutlineService outlineService = mock(OutlineService.class);
        AiService aiService = mock(AiService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        StoryController controller = new StoryController(storyService, outlineService, aiService, currentUserResolver);
        UUID storyId = UUID.randomUUID();
        OutlineCreateRequest request = new OutlineCreateRequest("新大纲", "world-1", Map.of("hook", "seed"));
        OutlineDto outline = new OutlineDto(
                UUID.randomUUID(),
                storyId,
                "新大纲",
                "world-1",
                Map.of("hook", "seed"),
                List.of(),
                Instant.now()
        );
        when(outlineService.createOutline(storyId, request)).thenReturn(outline);

        OutlineDto result = controller.createOutline(storyId, request);

        assertEquals(outline, result);
        verify(outlineService).createOutline(storyId, request);
    }

    @Test
    void controllerShouldNotKeepStoryRepositoryField() {
        var fieldTypes = Arrays.stream(StoryController.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .toList();

        assertFalse(fieldTypes.contains("StoryRepository"));
    }
}
