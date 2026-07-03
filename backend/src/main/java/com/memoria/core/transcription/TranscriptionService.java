package com.memoria.core.transcription;

import com.memoria.core.audio.AudioChunkRepository;
import com.memoria.core.audio.ChunkAudioEnregistreEvent;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TranscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(TranscriptionService.class);

    private final TranscriptionRepository transcriptionRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final TranscripteurPort transcripteur;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;

    public TranscriptionService(
            TranscriptionRepository transcriptionRepository,
            AudioChunkRepository audioChunkRepository,
            TranscripteurPort transcripteur,
            SessionService sessionService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.transcriptionRepository = transcriptionRepository;
        this.audioChunkRepository = audioChunkRepository;
        this.transcripteur = transcripteur;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
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
        signalerSiToutesLesTranscriptionsSontTerminees(evenement.sessionId());
    }

    private void enregistrer(ChunkAudioEnregistreEvent evenement, TranscriptionStatut statut, String texte) {
        Transcription transcription = new Transcription(evenement.sessionId(), evenement.numeroSequence(), texte, statut);
        transcriptionRepository.save(transcription);
    }

    private void signalerSiToutesLesTranscriptionsSontTerminees(UUID sessionId) {
        // Si la session est deja terminee et que ce chunk etait le dernier en
        // attente de transcription, le resume (declenche normalement a la fin
        // de session) a pu manquer ce segment car il n'etait pas encore pret :
        // on redonne une chance de le generer maintenant qu'il l'est.
        boolean sessionTerminee = sessionService.obtenirSession(sessionId).getStatut() == SessionStatus.TERMINEE;
        if (!sessionTerminee) {
            return;
        }
        if (audioChunkRepository.countBySessionId(sessionId) == transcriptionRepository.countBySessionId(sessionId)) {
            eventPublisher.publishEvent(new ToutesTranscriptionsTermineesEvent(sessionId));
        }
    }

    public List<Transcription> obtenirTranscriptions(UUID sessionId) {
        sessionService.obtenirSession(sessionId);
        return transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId);
    }
}
