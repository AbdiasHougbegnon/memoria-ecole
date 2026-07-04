package com.memoria.core.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        TypeDocument type,
        String nomFichier,
        String texteExtrait,
        StatutDocument statut,
        Instant dateCreation
) {
    public static DocumentResponse depuis(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getType(),
                document.getNomFichier(),
                document.getTexteExtrait(),
                document.getStatut(),
                document.getDateCreation()
        );
    }
}
