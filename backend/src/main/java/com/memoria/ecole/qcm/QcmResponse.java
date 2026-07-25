package com.memoria.ecole.qcm;

import java.time.Instant;
import java.util.List;

public record QcmResponse(
        List<QuestionQcmResponse> questions,
        List<Integer> segmentsSources,
        StatutQcm statut,
        Instant dateCreation
) {

    public static QcmResponse depuis(Qcm qcm) {
        return new QcmResponse(
                qcm.getQuestions().stream().map(QuestionQcmResponse::depuis).toList(),
                qcm.getSegmentsSources(),
                qcm.getStatut(),
                qcm.getDateCreation()
        );
    }
}
