package com.ainovel.app.admin.dto;

public record SlopReviewSampleAiReviewMatrixDto(
        int expectedTrueObservedTrue,
        int expectedTrueObservedFalse,
        int expectedFalseObservedTrue,
        int expectedFalseObservedFalse
) {
}
