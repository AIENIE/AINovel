package com.ainovel.app.quality;

import java.util.UUID;

public interface SlopQualityRecorder {
    UUID record(SlopQualityRecord record);
}
