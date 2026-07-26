package com.memoria.ecole.notion;

import java.util.UUID;

public class NotionCandidateNotFoundException extends RuntimeException {

    public NotionCandidateNotFoundException(UUID id) {
        super("Notion candidate introuvable : " + id);
    }
}
