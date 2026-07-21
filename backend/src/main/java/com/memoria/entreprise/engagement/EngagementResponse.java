package com.memoria.entreprise.engagement;

import java.time.Instant;
import java.util.UUID;

public record EngagementResponse(
        UUID id,
        UUID sessionId,
        String sessionTitre,
        String description,
        String responsable,
        String echeance,
        Instant dateEcheance,
        StatutEngagement statut,
        Instant dateCreation,
        Instant dateDerniereMaj
) {

    public static EngagementResponse depuis(Engagement engagement, String sessionTitre) {
        return new EngagementResponse(
                engagement.getId(),
                engagement.getSessionId(),
                sessionTitre,
                engagement.getDescription(),
                engagement.getResponsable(),
                engagement.getEcheance(),
                engagement.getDateEcheance(),
                engagement.getStatut(),
                engagement.getDateCreation(),
                engagement.getDateDerniereMaj()
        );
    }
}
