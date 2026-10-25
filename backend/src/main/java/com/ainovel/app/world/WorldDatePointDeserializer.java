package com.ainovel.app.world;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import static com.ainovel.app.world.WorldDraftDtos.DatePoint;

/** Keep author-defined date coordinates exact; Jackson's default integer coercion truncates decimals. */
public final class WorldDatePointDeserializer extends JsonDeserializer<DatePoint> {
    @Override
    public DatePoint deserialize(JsonParser parser,DeserializationContext context) throws IOException {
        JsonNode value=parser.getCodec().readTree(parser);
        if(value==null||!value.isObject())throw JsonMappingException.from(parser,"WORLD_TIMELINE_DATE_INVALID");
        return new DatePoint(integer(value,"year",parser),integer(value,"month",parser),integer(value,"day",parser));
    }

    private static Integer integer(JsonNode object,String field,JsonParser parser) throws JsonMappingException {
        var value=object.get(field);
        if(value==null||value.isNull())return null;
        if(!value.isIntegralNumber()||!value.canConvertToInt())throw JsonMappingException.from(parser,"WORLD_TIMELINE_DATE_INVALID");
        return value.intValue();
    }
}
