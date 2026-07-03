package com.memoria.core.audio;

import java.util.UUID;

public record ChunkAudioEnregistreEvent(UUID sessionId, int numeroSequence, byte[] donnees) {
}
