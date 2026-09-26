package com.ainovel.app.story.dto;

import java.time.Instant;
import java.util.UUID;

public record CharacterDto(UUID id, String name, String synopsis, String details, String relationships, String role, String archetype, Instant updatedAt) {}
