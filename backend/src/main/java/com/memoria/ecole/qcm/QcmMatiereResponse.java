package com.memoria.ecole.qcm;

import java.time.Instant;
import java.util.List;

public record QcmMatiereResponse(
        List<QuestionQcmResponse> questions,
        StatutQcm statut,
        Instant dateCreation
) {
    public static QcmMatiereResponse depuis(QcmMatiere qcm) {
        return new QcmMatiereResponse(
                qcm.getQuestions().stream().map(QuestionQcmResponse::depuis).toList(),
                qcm.getStatut(),
                qcm.getDateCreation()
        );
    }
}
