package com.memoria.core.audio;

import java.util.UUID;

public class AudioChunkNotFoundException extends RuntimeException {

    public AudioChunkNotFoundException(UUID sessionId, int numeroSequence) {
        super("Chunk audio introuvable : session " + sessionId + ", numero " + numeroSequence);
    }
}
