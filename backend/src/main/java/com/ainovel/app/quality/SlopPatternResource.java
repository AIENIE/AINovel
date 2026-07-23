package com.ainovel.app.quality;

import java.util.List;

record SlopPatternResource(int schemaVersion, List<SlopPatternRule> rules) {
    SlopPatternResource {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
