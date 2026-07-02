package com.memoria.core.audio;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AudioChunkService {

    private final AudioChunkRepository audioChunkRepository;
    private final StockageAudioPort stockageAudio;
    private final SessionService sessionService;

    public AudioChunkService(
            AudioChunkRepository audioChunkRepository,
            StockageAudioPort stockageAudio,
            SessionService sessionService
    ) {
        this.audioChunkRepository = audioChunkRepository;
        this.stockageAudio = stockageAudio;
        this.sessionService = sessionService;
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
        return new ResultatEnregistrementChunk(audioChunkRepository.save(chunk), false);
    }
}
