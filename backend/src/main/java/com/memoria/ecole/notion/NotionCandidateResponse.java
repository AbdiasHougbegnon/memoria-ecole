package com.memoria.ecole.notion;

import java.time.Instant;
import java.util.UUID;

public record NotionCandidateResponse(
        UUID id,
        UUID documentMatiereId,
        UUID matiereId,
        String terme,
        String definition,
        StatutNotionCandidate statut,
        Instant dateCreation
) {
    public static NotionCandidateResponse depuis(NotionCandidate candidate) {
        return new NotionCandidateResponse(
                candidate.getId(),
                candidate.getDocumentMatiereId(),
                candidate.getMatiereId(),
                candidate.getTerme(),
                candidate.getDefinition(),
                candidate.getStatut(),
                candidate.getDateCreation()
        );
    }
}
