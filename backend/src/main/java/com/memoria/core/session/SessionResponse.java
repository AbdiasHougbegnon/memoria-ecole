package com.memoria.core.session;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String titre,
        Instant dateCreation,
        SessionStatus statut,
        String cheminFichierAudio
) {

    public static SessionResponse depuis(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getTitre(),
                session.getDateCreation(),
                session.getStatut(),
                session.getCheminFichierAudio()
        );
    }
}
