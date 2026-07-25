package com.memoria.core.audio;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AudioChunkService {

    private final AudioChunkRepository audioChunkRepository;
    private final StockageAudioPort stockageAudio;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;

    public AudioChunkService(
            AudioChunkRepository audioChunkRepository,
            StockageAudioPort stockageAudio,
            SessionService sessionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.audioChunkRepository = audioChunkRepository;
        this.stockageAudio = stockageAudio;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
    }

    public ResultatEnregistrementChunk enregistrerChunk(UUID sessionId, int numeroSequence, byte[] donnees) {
        Session session = sessionService.obtenirSession(sessionId);
        if (session.getStatut() != SessionStatus.EN_COURS) {
            throw new SessionNonActiveException(sessionId);
        }

        Optional<AudioChunk> chunkExistant = audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, numeroSequence);
        if (chunkExistant.isPresent()) {
            return new ResultatEnregistrementChunk(chunkExistant.get(), true);
        }

        String cheminStockage = stockageAudio.sauvegarder(sessionId, numeroSequence, donnees);
        AudioChunk chunk = new AudioChunk(sessionId, numeroSequence, cheminStockage, donnees.length);
        AudioChunk chunkSauvegarde = audioChunkRepository.save(chunk);
        eventPublisher.publishEvent(new ChunkAudioEnregistreEvent(sessionId, numeroSequence, donnees));
        return new ResultatEnregistrementChunk(chunkSauvegarde, false);
    }

    // Reprise cote client apres coupure reseau/fermeture d'onglet : le client
    // demande ce qu'il a deja envoye avant de reprendre l'enregistrement, pour
    // repartir juste apres le dernier numero recu sans trou ni renvoi massif.
    public List<Integer> listerNumerosRecus(UUID sessionId) {
        sessionService.obtenirSession(sessionId);
        return audioChunkRepository.findNumerosSequenceBySessionId(sessionId);
    }

    // Drill-down resume/compte-rendu -> transcription -> audio (voir
    // docs/phases/phase-14-drilldown-source-audio.md) : jusqu'ici
    // stockageAudio.lire n'etait appele que par IdentificationLocuteurService,
    // jamais expose en HTTP.
    public byte[] obtenirAudio(UUID sessionId, int numeroSequence) {
        AudioChunk chunk = audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, numeroSequence)
                .orElseThrow(() -> new AudioChunkNotFoundException(sessionId, numeroSequence));
        return stockageAudio.lire(chunk.getCheminStockage());
    }
}
