package com.ainovel.app.common;

import java.math.BigDecimal;

/** Preserves an omitted field's default and rejects malformed present JSON values. */
public final class JsonValues {
    private JsonValues() { }

    public static int integer(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (!(value instanceof Number) && !(value instanceof String))
            throw new BusinessException("字段必须为整数");
        try {
            return new BigDecimal(value.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new BusinessException("字段必须为整数", ex);
        }
    }

    public static boolean bool(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new BusinessException("字段必须为布尔值");
    }
}
