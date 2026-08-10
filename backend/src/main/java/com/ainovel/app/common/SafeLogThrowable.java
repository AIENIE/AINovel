package com.ainovel.app.common;

import java.io.Serial;
import java.util.Objects;

/**
 * Throwable for diagnostic logs that keeps the original type marker and stack frames only.
 */
public final class SafeLogThrowable extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String originalType;

    private SafeLogThrowable(Throwable source) {
        super(null, null, false, true);
        this.originalType = source.getClass().getName();
        setStackTrace(source.getStackTrace());
    }

    public static SafeLogThrowable stackOnly(Throwable source) {
        return new SafeLogThrowable(Objects.requireNonNull(source, "source"));
    }

    public String originalType() {
        return originalType;
    }
}
