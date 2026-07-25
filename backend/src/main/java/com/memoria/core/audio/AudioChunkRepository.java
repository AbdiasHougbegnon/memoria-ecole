package com.memoria.core.audio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AudioChunkRepository extends JpaRepository<AudioChunk, UUID> {

    Optional<AudioChunk> findBySessionIdAndNumeroSequence(UUID sessionId, int numeroSequence);

    long countBySessionId(UUID sessionId);

    // Reprise cote client (voir AudioChunkService.listerNumerosRecus) : permet
    // a un client reconnecte de savoir a quel numero reprendre sans rejouer
    // tout l'historique.
    @Query("select c.numeroSequence from AudioChunk c where c.sessionId = :sessionId order by c.numeroSequence asc")
    List<Integer> findNumerosSequenceBySessionId(@Param("sessionId") UUID sessionId);

    // Rattrapage des transcriptions perdues (voir TranscriptionService) : un
    // chunk deja recu depuis plus de "avant" mais sans Transcription
    // correspondante a probablement perdu son evenement @Async (redemarrage
    // serveur entre la sauvegarde du chunk et l'execution du listener).
    @Query("select c from AudioChunk c where c.dateReception < :avant "
            + "and not exists (select 1 from Transcription t where t.sessionId = c.sessionId and t.numeroSequence = c.numeroSequence)")
    List<AudioChunk> findChunksSansTranscriptionAvant(@Param("avant") Instant avant);
}
