package com.ainovel.app.manuscript;

/**
 * Controls whether the generation pipeline uses the standard fast path or the
 * crafted path that injects negative slop-pattern constraints and human-trace
 * element prompts.
 */
public enum GenerationMode {
    /** Standard single-pass generation (default). No extra constraint injection. */
    FAST,
    /** Crafted mode: injects sampled negative patterns and human-trace elements before drafting. */
    CRAFTED
}
