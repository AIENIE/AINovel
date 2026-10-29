package com.ainovel.app.worldassistant;

import com.ainovel.app.common.ApiStatusException;
import org.springframework.http.HttpStatus;

final class WorldAssistantErrors {
    private WorldAssistantErrors() {}
    static ApiStatusException invalid(String code){return new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY,code);}
    static ApiStatusException conflict(String code){return new ApiStatusException(HttpStatus.CONFLICT,code);}
    static ApiStatusException missing(){return new ApiStatusException(HttpStatus.NOT_FOUND,"WORLD_ASSISTANT_NOT_FOUND");}
    static void key(String value){if(value==null||!value.matches("[A-Za-z0-9:_-]{8,128}"))throw invalid("INVALID_IDEMPOTENCY_KEY");}
}
