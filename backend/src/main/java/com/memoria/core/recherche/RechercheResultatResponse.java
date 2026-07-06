package com.memoria.core.recherche;

import java.time.Instant;
import java.util.UUID;

public record RechercheResultatResponse(
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

    public static RechercheResultatResponse depuis(ResultatRecherche resultat) {
        return new RechercheResultatResponse(
                resultat.sessionId(),
                resultat.titreSession(),
                resultat.dateSession(),
                resultat.texte(),
                resultat.locuteur(),
                resultat.offsetMillisecondes(),
                resultat.dureeMillisecondes(),
                resultat.numeroSequence(),
                resultat.score()
        );
    }
}
