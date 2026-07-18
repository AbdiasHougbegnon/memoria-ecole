package com.memoria.ecole.resumecours;

import java.util.UUID;

public class AucuneTranscriptionDisponibleException extends RuntimeException {

    public AucuneTranscriptionDisponibleException(UUID sessionId) {
        super("Aucune transcription reussie pour la session " + sessionId + " : rien a resumer");
    }
}
