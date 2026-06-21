package com.ainovel.app.admin.dto;

import java.util.List;

public record SlopReviewSampleImportResultDto(
        int imported,
        int skipped,
        List<String> errors,
        List<SlopReviewSampleDto> samples
) {
}
