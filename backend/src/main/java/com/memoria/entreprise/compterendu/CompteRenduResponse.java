package com.memoria.entreprise.compterendu;

import java.time.Instant;
import java.util.List;

public record CompteRenduResponse(
        String synthese,
        List<String> decisions,
        List<ActionCompteRenduResponse> actions,
        List<Integer> segmentsSources,
        StatutCompteRendu statut,
        Instant dateCreation
) {

    public static CompteRenduResponse depuis(CompteRendu compteRendu) {
        return new CompteRenduResponse(
                compteRendu.getSynthese(),
                compteRendu.getDecisions(),
                compteRendu.getActions().stream().map(ActionCompteRenduResponse::depuis).toList(),
                compteRendu.getSegmentsSources(),
                compteRendu.getStatut(),
                compteRendu.getDateCreation()
        );
    }
}
