package com.memoria.core.audio;

import java.time.Instant;
import java.util.UUID;

public record EnregistrerChunkResponse(
        UUID sessionId,
        int numeroSequence,
        Instant dateReception,
        boolean dejaRecu
) {

    public static EnregistrerChunkResponse depuis(ResultatEnregistrementChunk resultat) {
        AudioChunk chunk = resultat.chunk();
        return new EnregistrerChunkResponse(
                chunk.getSessionId(),
                chunk.getNumeroSequence(),
                chunk.getDateReception(),
                resultat.dejaRecu()
        );
    }
}
