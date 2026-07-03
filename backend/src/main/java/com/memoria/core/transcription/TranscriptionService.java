package com.memoria.core.transcription;

import com.memoria.core.audio.ChunkAudioEnregistreEvent;
import com.memoria.core.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TranscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(TranscriptionService.class);

    private final TranscriptionRepository transcriptionRepository;
    private final TranscripteurPort transcripteur;
    private final SessionService sessionService;

    public TranscriptionService(
            TranscriptionRepository transcriptionRepository,
            TranscripteurPort transcripteur,
            SessionService sessionService
    ) {
        this.transcriptionRepository = transcriptionRepository;
        this.transcripteur = transcripteur;
        this.sessionService = sessionService;
    }

    @Async
    @EventListener
    public void surChunkEnregistre(ChunkAudioEnregistreEvent evenement) {
        try {
            String texte = transcripteur.transcrire(evenement.donnees());
            enregistrer(evenement, TranscriptionStatut.REUSSIE, texte);
        } catch (Exception e) {
            LOG.warn("Echec de la transcription du chunk {} de la session {}", evenement.numeroSequence(), evenement.sessionId(), e);
            enregistrer(evenement, TranscriptionStatut.ECHEC, null);
        }
    }

    private void enregistrer(ChunkAudioEnregistreEvent evenement, TranscriptionStatut statut, String texte) {
        Transcription transcription = new Transcription(evenement.sessionId(), evenement.numeroSequence(), texte, statut);
        transcriptionRepository.save(transcription);
    }

    public List<Transcription> obtenirTranscriptions(UUID sessionId) {
        sessionService.obtenirSession(sessionId);
        return transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId);
    }
}
