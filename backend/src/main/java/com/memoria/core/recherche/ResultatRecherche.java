package com.memoria.core.recherche;

import java.time.Instant;
import java.util.UUID;

public record ResultatRecherche(
        UUID sessionId,
        String titreSession,
        Instant dateSession,
        String texte,
        int locuteur,
        long offsetMillisecondes,
        long dureeMillisecondes,
        int numeroSequence,
        double score
) {
}
