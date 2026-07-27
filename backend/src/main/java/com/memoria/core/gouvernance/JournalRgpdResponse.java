package com.memoria.core.gouvernance;

import java.time.Instant;
import java.util.UUID;

public record JournalRgpdResponse(
        UUID id,
        TypeActionRgpd type,
        UUID utilisateurCibleId,
        UUID initiateurId,
        Instant dateAction,
        String details
) {
    public static JournalRgpdResponse depuis(JournalRgpd journal) {
        return new JournalRgpdResponse(
                journal.getId(),
                journal.getType(),
                journal.getUtilisateurCibleId(),
                journal.getInitiateurId(),
                journal.getDateAction(),
                journal.getDetails()
        );
    }
}
