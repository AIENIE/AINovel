package com.ainovel.app.aioperation;

import java.util.function.BiConsumer;

/** Scene stages share the operation's existing output-token accumulator. */
public final class SceneGenerationProgress {
    private static final ThreadLocal<BiConsumer<String,Integer>> CURRENT=new ThreadLocal<>();
    private SceneGenerationProgress() {}
    public static AutoCloseable install(BiConsumer<String,Integer> listener) {
        CURRENT.set(listener);
        return CURRENT::remove;
    }
    public static void report(String label,int completed) {
        var listener=CURRENT.get();
        if(listener!=null) listener.accept(label,completed);
    }
}
