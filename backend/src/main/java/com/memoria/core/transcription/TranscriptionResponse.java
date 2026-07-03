package com.memoria.core.transcription;

import java.time.Instant;

public record TranscriptionResponse(
        int numeroSequence,
        String texte,
        TranscriptionStatut statut,
        Instant dateCreation
) {

    public static TranscriptionResponse depuis(Transcription transcription) {
        return new TranscriptionResponse(
                transcription.getNumeroSequence(),
                transcription.getTexte(),
                transcription.getStatut(),
                transcription.getDateCreation()
        );
    }
}
