package com.memoria.ecole.matiere;

import java.time.Instant;
import java.util.UUID;

public record MatiereResponse(UUID id, String nom, UUID couloirId, UUID createurId, Instant dateCreation) {

    public static MatiereResponse depuis(Matiere matiere) {
        return new MatiereResponse(matiere.getId(), matiere.getNom(), matiere.getCouloirId(), matiere.getCreateurId(), matiere.getDateCreation());
    }
}
