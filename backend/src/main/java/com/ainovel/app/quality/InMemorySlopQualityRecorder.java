package com.ainovel.app.quality;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemorySlopQualityRecorder implements SlopQualityRecorder {
    private final List<SlopQualityRecord> recorded = new ArrayList<>();

    @Override
    public UUID record(SlopQualityRecord record) {
        recorded.add(record);
        return UUID.randomUUID();
    }

    public List<SlopQualityRecord> recorded() {
        return List.copyOf(recorded);
    }
}
