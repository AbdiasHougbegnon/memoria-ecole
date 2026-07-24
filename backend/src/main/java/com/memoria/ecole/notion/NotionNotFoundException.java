package com.memoria.ecole.notion;

import java.util.UUID;

public class NotionNotFoundException extends RuntimeException {

    public NotionNotFoundException(UUID id) {
        super("Notion introuvable : " + id);
    }
}
