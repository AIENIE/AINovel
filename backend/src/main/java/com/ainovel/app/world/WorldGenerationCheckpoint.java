package com.ainovel.app.world;

import com.ainovel.app.world.dto.WorldDetailDto;

import java.util.function.Supplier;

/** A task's frozen content guard and durable field outputs; model calls stay outside transactions. */
public interface WorldGenerationCheckpoint {
    void validate(WorldDetailDto current);
    String field(String moduleKey, String fieldKey, Supplier<String> generate);
    void applied(String moduleKey, WorldDetailDto updated);
}
