package com.ainovel.app.manuscript.attribution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnicodeCodePointDiffTest {

    @Test
    void equalLengthRewriteRemainsVisible() {
        UnicodeCodePointDiff.Result result = UnicodeCodePointDiff.calculate("甲😀乙", "甲🚀乙");

        assertEquals(3, result.beforeCodePoints());
        assertEquals(3, result.afterCodePoints());
        assertEquals(2, result.retainedCodePoints());
        assertEquals(1, result.insertedCodePoints());
        assertEquals(1, result.deletedCodePoints());
        assertFalse(result.exactMatch());
        assertTrue(result.operations().stream().anyMatch(operation ->
                operation.type() == UnicodeCodePointDiff.OperationType.DELETE && "😀".equals(operation.text())));
        assertTrue(result.operations().stream().anyMatch(operation ->
                operation.type() == UnicodeCodePointDiff.OperationType.INSERT && "🚀".equals(operation.text())));
    }

    @Test
    void supplementaryCharacterCountsAsOneCodePoint() {
        UnicodeCodePointDiff.Result result = UnicodeCodePointDiff.calculate("😀", "");

        assertEquals(1, result.beforeCodePoints());
        assertEquals(0, result.afterCodePoints());
        assertEquals(1, result.deletedCodePoints());
    }

    @Test
    void unchangedTextIsExact() {
        UnicodeCodePointDiff.Result result = UnicodeCodePointDiff.calculate("山雨欲来", "山雨欲来");

        assertTrue(result.exactMatch());
        assertEquals(4, result.retainedCodePoints());
        assertEquals(1.0d, result.retainedRatio());
    }
}
