package com.ainovel.app.story.model;

import com.ainovel.app.common.BusinessException;
import java.util.Map;

/** Shared contract for scene planning, prompts and generated text validation. */
public record SceneLengthRange(int minHan, int maxHan) {
    public static SceneLengthRange from(Map<String, Object> planning) {
        Object min = planning == null ? null : planning.get("minHan");
        Object max = planning == null ? null : planning.get("maxHan");
        if (min == null && max == null) return new SceneLengthRange(2800, 3200);
        int lower = integer(min);
        int upper = integer(max);
        if (lower < 1 || upper < lower || upper > 20000) {
            throw new BusinessException("场景目标篇幅须满足 1 ≤ 最少汉字 ≤ 最多汉字 ≤ 20000");
        }
        return new SceneLengthRange(lower, upper);
    }

    private static int integer(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue()) {
            throw new BusinessException("请同时设置场景目标篇幅的最少和最多汉字，且使用整数");
        }
        return number.intValue();
    }
}
