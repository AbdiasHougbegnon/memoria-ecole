package com.memoria.ecole.qcm;

import java.time.Instant;
import java.util.List;

public record TentativeQcmResponse(
        List<Integer> reponsesChoisies,
        int score,
        int nombreQuestions,
        int nombreTentatives,
        Instant dateMiseAJour
) {

    public static TentativeQcmResponse depuis(TentativeQcm tentative) {
        return new TentativeQcmResponse(
                tentative.getReponsesChoisies(),
                tentative.getScore(),
                tentative.getNombreQuestions(),
                tentative.getNombreTentatives(),
                tentative.getDateMiseAJour()
        );
    }
}
