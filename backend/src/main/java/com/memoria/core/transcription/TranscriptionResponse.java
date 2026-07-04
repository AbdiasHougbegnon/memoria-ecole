package com.memoria.core.transcription;

import java.time.Instant;
import java.util.List;

public record TranscriptionResponse(
        int numeroSequence,
        String texte,
        TranscriptionStatut statut,
        Instant dateCreation,
        List<SegmentLocuteurResponse> segmentsLocuteur
) {

    public static TranscriptionResponse depuis(Transcription transcription) {
        return new TranscriptionResponse(
                transcription.getNumeroSequence(),
                transcription.getTexte(),
                transcription.getStatut(),
                transcription.getDateCreation(),
                transcription.getSegmentsLocuteur().stream()
                        .map(SegmentLocuteurResponse::depuis)
                        .toList()
        );
    }
}
