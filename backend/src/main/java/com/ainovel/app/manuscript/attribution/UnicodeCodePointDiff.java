package com.ainovel.app.manuscript.attribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Exact Myers diff over Unicode code points. UTF-16 surrogate pairs are one unit,
 * and replacements remain visible even when the before/after lengths are equal.
 */
public final class UnicodeCodePointDiff {
    private UnicodeCodePointDiff() {}

    public static Result calculate(String beforeText, String afterText) {
        int[] before = safe(beforeText).codePoints().toArray();
        int[] after = safe(afterText).codePoints().toArray();
        List<AtomicOperation> reversed = shortestEditScript(before, after);
        Collections.reverse(reversed);
        List<Operation> operations = coalesce(reversed);

        int retained = 0;
        int inserted = 0;
        int deleted = 0;
        for (Operation operation : operations) {
            switch (operation.type()) {
                case EQUAL -> retained += operation.codePointCount();
                case INSERT -> inserted += operation.codePointCount();
                case DELETE -> deleted += operation.codePointCount();
            }
        }
        double retainedRatio = before.length == 0
                ? (after.length == 0 ? 1.0d : 0.0d)
                : (double) retained / before.length;
        return new Result(
                before.length,
                after.length,
                retained,
                inserted,
                deleted,
                inserted == 0 && deleted == 0,
                retainedRatio,
                operations
        );
    }

    private static List<AtomicOperation> shortestEditScript(int[] before, int[] after) {
        int beforeLength = before.length;
        int afterLength = after.length;
        int max = beforeLength + afterLength;
        int offset = max + 1;
        int[] frontier = new int[(2 * max) + 3];
        List<int[]> trace = new ArrayList<>();

        for (int distance = 0; distance <= max; distance++) {
            trace.add(Arrays.copyOf(frontier, frontier.length));
            for (int diagonal = -distance; diagonal <= distance; diagonal += 2) {
                int index = offset + diagonal;
                int x;
                if (diagonal == -distance
                        || (diagonal != distance && frontier[index - 1] < frontier[index + 1])) {
                    x = frontier[index + 1];
                } else {
                    x = frontier[index - 1] + 1;
                }
                int y = x - diagonal;
                while (x < beforeLength && y < afterLength && before[x] == after[y]) {
                    x++;
                    y++;
                }
                frontier[index] = x;
                if (x >= beforeLength && y >= afterLength) {
                    return backtrack(before, after, trace, distance, offset);
                }
            }
        }
        throw new IllegalStateException("Unable to calculate Unicode diff");
    }

    private static List<AtomicOperation> backtrack(
            int[] before,
            int[] after,
            List<int[]> trace,
            int finalDistance,
            int offset
    ) {
        int x = before.length;
        int y = after.length;
        List<AtomicOperation> reversed = new ArrayList<>();

        for (int distance = finalDistance; distance > 0; distance--) {
            int[] frontier = trace.get(distance);
            int diagonal = x - y;
            int index = offset + diagonal;
            int previousDiagonal;
            if (diagonal == -distance
                    || (diagonal != distance && frontier[index - 1] < frontier[index + 1])) {
                previousDiagonal = diagonal + 1;
            } else {
                previousDiagonal = diagonal - 1;
            }
            int previousX = frontier[offset + previousDiagonal];
            int previousY = previousX - previousDiagonal;

            while (x > previousX && y > previousY) {
                reversed.add(new AtomicOperation(OperationType.EQUAL, before[x - 1]));
                x--;
                y--;
            }
            if (x == previousX) {
                reversed.add(new AtomicOperation(OperationType.INSERT, after[y - 1]));
                y--;
            } else {
                reversed.add(new AtomicOperation(OperationType.DELETE, before[x - 1]));
                x--;
            }
        }
        while (x > 0 && y > 0) {
            reversed.add(new AtomicOperation(OperationType.EQUAL, before[x - 1]));
            x--;
            y--;
        }
        while (x > 0) {
            reversed.add(new AtomicOperation(OperationType.DELETE, before[--x]));
        }
        while (y > 0) {
            reversed.add(new AtomicOperation(OperationType.INSERT, after[--y]));
        }
        return reversed;
    }

    private static List<Operation> coalesce(List<AtomicOperation> atomicOperations) {
        if (atomicOperations.isEmpty()) {
            return List.of();
        }
        List<Operation> result = new ArrayList<>();
        int beforeCursor = 0;
        int afterCursor = 0;
        int index = 0;
        while (index < atomicOperations.size()) {
            AtomicOperation first = atomicOperations.get(index);
            OperationType type = first.type();
            int beforeStart = beforeCursor;
            int afterStart = afterCursor;
            StringBuilder text = new StringBuilder();
            int count = 0;
            while (index < atomicOperations.size() && atomicOperations.get(index).type() == type) {
                int codePoint = atomicOperations.get(index).codePoint();
                text.appendCodePoint(codePoint);
                count++;
                if (type != OperationType.INSERT) {
                    beforeCursor++;
                }
                if (type != OperationType.DELETE) {
                    afterCursor++;
                }
                index++;
            }
            result.add(new Operation(type, beforeStart, afterStart, count, text.toString()));
        }
        return List.copyOf(result);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public enum OperationType {
        EQUAL,
        INSERT,
        DELETE
    }

    public record Operation(
            OperationType type,
            int beforeStart,
            int afterStart,
            int codePointCount,
            String text
    ) {}

    public record Result(
            int beforeCodePoints,
            int afterCodePoints,
            int retainedCodePoints,
            int insertedCodePoints,
            int deletedCodePoints,
            boolean exactMatch,
            double retainedRatio,
            List<Operation> operations
    ) {}

    private record AtomicOperation(OperationType type, int codePoint) {}
}
