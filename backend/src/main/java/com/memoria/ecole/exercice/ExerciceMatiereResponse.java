package com.memoria.ecole.exercice;

import com.memoria.ecole.qcm.StatutQcm;

import java.time.Instant;
import java.util.List;

public record ExerciceMatiereResponse(
        List<QuestionSaisieLibreResponse> questions,
        StatutQcm statut,
        Instant dateCreation
) {
    public static ExerciceMatiereResponse depuis(ExerciceMatiere exercice) {
        return new ExerciceMatiereResponse(
                exercice.getQuestions().stream().map(QuestionSaisieLibreResponse::depuis).toList(),
                exercice.getStatut(),
                exercice.getDateCreation()
        );
    }
}
