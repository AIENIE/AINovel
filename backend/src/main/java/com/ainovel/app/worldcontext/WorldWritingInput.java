package com.ainovel.app.worldcontext;

import com.fasterxml.jackson.databind.JsonNode;

/** Server-created quote snapshot, persisted with the existing model selections and operation. */
public record WorldWritingInput(String purpose,String contextHash,long selectionRevision,JsonNode frozen) {}
