package com.memoria.ecole.tuteurvocal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EtatTutoratResponse(
        UUID id,
        UUID seanceId,
        StatutSeanceTutorat statut,
        UUID notionCouranteId,
        boolean modeExercice,
        Instant dateDebut,
        Instant dateFin,
        List<TourResponse> tours
) {

    public static EtatTutoratResponse depuis(SeanceTutorat seanceTutorat, List<TourDialogueTutorat> tours) {
        return new EtatTutoratResponse(
                seanceTutorat.getId(),
                seanceTutorat.getSeanceId(),
                seanceTutorat.getStatut(),
                seanceTutorat.getNotionCouranteId(),
                seanceTutorat.isModeExercice(),
                seanceTutorat.getDateDebut(),
                seanceTutorat.getDateFin(),
                tours.stream().map(TourResponse::depuis).toList()
        );
    }
}
