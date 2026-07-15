package com.memoria.entreprise.engagement;

import java.util.UUID;

public class EngagementNotFoundException extends RuntimeException {

    public EngagementNotFoundException(UUID id) {
        super("Aucun engagement trouve pour l'id " + id);
    }
}
