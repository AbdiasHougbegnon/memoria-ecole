package com.memoria.ecole.qcm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QcmMatiereResponse(
        List<QuestionQcmResponse> questions,
        StatutQcm statut,
        Instant dateCreation,
        List<UUID> notionIds
) {
    public static QcmMatiereResponse depuis(QcmMatiere qcm, List<UUID> notionIds) {
        return new QcmMatiereResponse(
                qcm.getQuestions().stream().map(QuestionQcmResponse::depuis).toList(),
                qcm.getStatut(),
                qcm.getDateCreation(),
                notionIds
        );
    }
}
