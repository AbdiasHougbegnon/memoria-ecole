package com.memoria.ecole.document;

import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.TypeDocument;

import java.time.Instant;
import java.util.UUID;

public record DocumentMatiereResponse(
        UUID id,
        UUID matiereId,
        TypeDocument type,
        String nomFichier,
        long taille,
        String texteExtrait,
        StatutDocument statut,
        Instant dateCreation
) {
    public static DocumentMatiereResponse depuis(DocumentMatiere document) {
        return new DocumentMatiereResponse(
                document.getId(),
                document.getMatiereId(),
                document.getType(),
                document.getNomFichier(),
                document.getTaille(),
                document.getTexteExtrait(),
                document.getStatut(),
                document.getDateCreation()
        );
    }
}
