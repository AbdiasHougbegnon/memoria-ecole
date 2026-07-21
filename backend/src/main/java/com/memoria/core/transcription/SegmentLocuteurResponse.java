package com.memoria.core.transcription;

import java.util.Map;
import java.util.UUID;

public record SegmentLocuteurResponse(
        int locuteur,
        String texte,
        long offsetMillisecondes,
        long dureeMillisecondes,
        UUID utilisateurIdentifieId,
        String nomUtilisateurIdentifie,
        Double confianceIdentification
) {

    public static SegmentLocuteurResponse depuis(SegmentLocuteur segment, Map<UUID, String> nomsParUtilisateurId) {
        UUID id = segment.getUtilisateurIdentifieId();
        return new SegmentLocuteurResponse(
                segment.getLocuteur(),
                segment.getTexte(),
                segment.getOffsetMillisecondes(),
                segment.getDureeMillisecondes(),
                id,
                id == null ? null : nomsParUtilisateurId.get(id),
                segment.getConfianceIdentification()
        );
    }
}
