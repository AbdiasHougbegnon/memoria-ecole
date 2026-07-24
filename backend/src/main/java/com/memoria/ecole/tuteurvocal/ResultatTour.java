package com.memoria.ecole.tuteurvocal;

import com.memoria.ecole.notion.NiveauMaitrise;

import java.util.UUID;

public record ResultatTour(
        UUID seanceTutoratId,
        UUID tourId,
        String texteTuteur,
        UUID notionCouranteId,
        NiveauMaitrise niveauMaitrise,
        boolean seanceTerminee
) {
}
