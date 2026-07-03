package com.memoria.core.transcription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TranscriptionRepository extends JpaRepository<Transcription, UUID> {

    List<Transcription> findBySessionIdOrderByNumeroSequenceAsc(UUID sessionId);
}
