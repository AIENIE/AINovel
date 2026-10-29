package com.ainovel.app.settings.dto;

import java.util.Map;

public record WorldPromptTemplatesUpdateRequest(Map<String, String> modules,
                                                Map<String, String> finalTemplates,
                                                String fieldRefine, Map<String,String> actions) {
    public WorldPromptTemplatesUpdateRequest(Map<String,String> modules,Map<String,String> finalTemplates,String fieldRefine) { this(modules,finalTemplates,fieldRefine,null); }
}
