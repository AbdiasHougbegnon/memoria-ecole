package com.memoria.ecole.notion;

import java.time.Instant;
import java.util.UUID;

public record NotionResponse(UUID id, UUID matiereId, String terme, String definition, int ordre, Instant dateCreation) {

    public static NotionResponse depuis(Notion notion) {
        return new NotionResponse(notion.getId(), notion.getMatiereId(), notion.getTerme(), notion.getDefinition(), notion.getOrdre(), notion.getDateCreation());
    }
}
