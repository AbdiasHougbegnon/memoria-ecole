package com.memoria.ecole.tuteurvocal;

import com.memoria.ecole.notion.NiveauMaitrise;

import java.util.UUID;

// audioUrl pointe vers l'endpoint audio dedie plutot que d'embarquer l'audio
// en base64 dans le JSON -- voir docs/phases/phase-9-tuteur-vocal.md.
public record ResultatTourResponse(
        UUID seanceTutoratId,
        UUID tourId,
        String texteTuteur,
        String audioUrl,
        UUID notionCouranteId,
        NiveauMaitrise niveauMaitrise,
        boolean seanceTerminee
) {

    public static ResultatTourResponse depuis(ResultatTour resultat) {
        String audioUrl = resultat.tourId() != null
                ? "/api/v1/tutorat/" + resultat.seanceTutoratId() + "/audio/" + resultat.tourId()
                : null;
        return new ResultatTourResponse(
                resultat.seanceTutoratId(), resultat.tourId(), resultat.texteTuteur(), audioUrl,
                resultat.notionCouranteId(), resultat.niveauMaitrise(), resultat.seanceTerminee()
        );
    }
}
