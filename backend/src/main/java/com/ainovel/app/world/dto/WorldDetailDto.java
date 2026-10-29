package com.ainovel.app.world.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorldDetailDto(UUID id,
                             String name,
                             String tagline,
                             String status,
                             String version,
                             List<String> themes,
                             String creativeIntent,
                             String notes,
                             Map<String, Map<String, String>> modules,
                             Map<String, String> moduleProgress,
                             Instant updatedAt,
                             long revision, String overview) {
    public WorldDetailDto(UUID id,String name,String tagline,String status,String version,List<String> themes,String creativeIntent,String notes,Map<String,Map<String,String>> modules,Map<String,String> moduleProgress,Instant updatedAt,long revision) {
        this(id,name,tagline,status,version,themes,creativeIntent,notes,modules,moduleProgress,updatedAt,revision,null);
    }
    public WorldDetailDto(UUID id, String name, String tagline, String status, String version,
                          List<String> themes, String creativeIntent, String notes,
                          Map<String, Map<String, String>> modules, Map<String, String> moduleProgress,
                          Instant updatedAt) {
        this(id, name, tagline, status, version, themes, creativeIntent, notes, modules, moduleProgress, updatedAt, 0);
    }
}
