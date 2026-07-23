package com.ainovel.app.quality;

import java.util.List;

record SlopPatternCatalog(
        int schemaVersion,
        int windowChars,
        int sameFamilyMediumCount,
        int sameFamilyHighCount,
        int categoryMediumCount,
        int categoryHighCount,
        int issueCap,
        List<String> resources
) {
    SlopPatternCatalog {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
