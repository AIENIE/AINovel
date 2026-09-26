package com.ainovel.app.story;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.story.model.SceneLengthRange;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class SceneLengthRangeTest {
    @Test void keepsLegacyDefaultAndAcceptsShortScenes() {
        assertEquals(new SceneLengthRange(2800, 3200), SceneLengthRange.from(null));
        assertEquals(new SceneLengthRange(600, 900), SceneLengthRange.from(Map.of("minHan", 600, "maxHan", 900)));
    }
    @Test void rejectsIncompleteReversedFractionalAndUnboundedRanges() {
        for (Map<String, Object> invalid : java.util.List.<Map<String, Object>>of(
                Map.of("minHan", 600), Map.of("minHan", 900, "maxHan", 600),
                Map.of("minHan", 1.5, "maxHan", 900), Map.of("minHan", 0, "maxHan", 900),
                Map.of("minHan", 600, "maxHan", 20001))) {
            assertThrows(BusinessException.class, () -> SceneLengthRange.from(invalid));
        }
    }
}
