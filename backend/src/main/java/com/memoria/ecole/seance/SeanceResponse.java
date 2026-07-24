package com.memoria.ecole.seance;

import java.time.Instant;
import java.util.UUID;

public record SeanceResponse(UUID id, String titre, UUID matiereId, UUID couloirId, UUID sessionId, Instant dateCreation) {

    public static SeanceResponse depuis(Seance seance) {
        return new SeanceResponse(seance.getId(), seance.getTitre(), seance.getMatiereId(), seance.getCouloirId(), seance.getSessionId(), seance.getDateCreation());
    }
}
