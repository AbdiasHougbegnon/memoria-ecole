package com.memoria.ecole.exercice;

import java.time.Instant;
import java.util.List;

public record TentativeExerciceSaisieLibreResponse(
        List<ReponseEvalueeResponse> reponses,
        int nombreTentatives,
        Instant dateMiseAJour
) {
    public static TentativeExerciceSaisieLibreResponse depuis(TentativeExerciceSaisieLibre tentative) {
        return new TentativeExerciceSaisieLibreResponse(
                tentative.getReponses().stream().map(ReponseEvalueeResponse::depuis).toList(),
                tentative.getNombreTentatives(),
                tentative.getDateMiseAJour()
        );
    }
}
