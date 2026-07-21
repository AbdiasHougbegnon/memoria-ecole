package com.memoria.core.transcription;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TranscriptionResponse(
        int numeroSequence,
        String texte,
        TranscriptionStatut statut,
        Instant dateCreation,
        List<SegmentLocuteurResponse> segmentsLocuteur
) {

    public static TranscriptionResponse depuis(Transcription transcription, Map<UUID, String> nomsParUtilisateurId) {
        return new TranscriptionResponse(
                transcription.getNumeroSequence(),
                transcription.getTexte(),
                transcription.getStatut(),
                transcription.getDateCreation(),
                transcription.getSegmentsLocuteur().stream()
                        .map(segment -> SegmentLocuteurResponse.depuis(segment, nomsParUtilisateurId))
                        .toList()
        );
    }
}
