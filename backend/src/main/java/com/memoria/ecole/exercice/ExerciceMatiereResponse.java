package com.memoria.ecole.exercice;

import com.memoria.ecole.qcm.StatutQcm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExerciceMatiereResponse(
        List<QuestionSaisieLibreResponse> questions,
        StatutQcm statut,
        Instant dateCreation,
        List<UUID> notionIds
) {
    public static ExerciceMatiereResponse depuis(ExerciceMatiere exercice, List<UUID> notionIds) {
        return new ExerciceMatiereResponse(
                exercice.getQuestions().stream().map(QuestionSaisieLibreResponse::depuis).toList(),
                exercice.getStatut(),
                exercice.getDateCreation(),
                notionIds
        );
    }
}
