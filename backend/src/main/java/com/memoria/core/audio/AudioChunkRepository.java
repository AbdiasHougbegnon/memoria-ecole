package com.memoria.core.audio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AudioChunkRepository extends JpaRepository<AudioChunk, UUID> {

    Optional<AudioChunk> findBySessionIdAndNumeroSequence(UUID sessionId, int numeroSequence);
}
