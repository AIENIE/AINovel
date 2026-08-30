package com.ainovel.app.v2;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class V2ExportDtos {
    private V2ExportDtos() { }
    public record CreateJobRequest(@Pattern(regexp = "(?i)txt|docx|epub|pdf") String format,
                                   UUID templateId, JsonNode config, @Size(max = 200) String chapterRange) { }
    public record TemplateRequest(@Size(max = 100) String name, @Size(max = 500) String description,
                                  @Pattern(regexp = "(?i)txt|docx|epub|pdf") String format,
                                  JsonNode config, Boolean isDefault) { }
    public record JobResponse(UUID id, UUID userId, UUID storyId, UUID manuscriptId, UUID templateId,
                              String format, JsonNode config, String chapterRange, String status, int progress,
                              String fileName, String filePath, long fileSizeBytes, String checksum,
                              String errorMessage, String contentType, Instant expiresAt, Instant createdAt,
                              Instant startedAt, Instant completedAt) { }
    public record TemplateResponse(UUID id, UUID userId, String name, String description, String format,
                                   JsonNode config, boolean isDefault, Instant createdAt, Instant updatedAt) { }
}
