package com.memoria.core.transcription;

import com.memoria.core.audio.AudioChunk;
import com.memoria.core.audio.AudioChunkRepository;
import com.memoria.core.audio.ChunkAudioEnregistreEvent;
import com.memoria.core.audio.StockageAudioPort;
import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TranscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(TranscriptionService.class);

    private final TranscriptionRepository transcriptionRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final TranscripteurPort transcripteur;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final UtilisateurRepository utilisateurRepository;
    private final StockageAudioPort stockageAudio;
    private final long delaiGraceRattrapageSecondes;

    public TranscriptionService(
            TranscriptionRepository transcriptionRepository,
            AudioChunkRepository audioChunkRepository,
            TranscripteurPort transcripteur,
            SessionService sessionService,
            ApplicationEventPublisher eventPublisher,
            UtilisateurRepository utilisateurRepository,
            StockageAudioPort stockageAudio,
            @Value("${memoria.transcription.rattrapage.delai-grace-secondes:120}") long delaiGraceRattrapageSecondes
    ) {
        this.transcriptionRepository = transcriptionRepository;
        this.audioChunkRepository = audioChunkRepository;
        this.transcripteur = transcripteur;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
        this.utilisateurRepository = utilisateurRepository;
        this.stockageAudio = stockageAudio;
        this.delaiGraceRattrapageSecondes = delaiGraceRattrapageSecondes;
    }

    @Async
    @EventListener
    public void surChunkEnregistre(ChunkAudioEnregistreEvent evenement) {
        traiterChunk(evenement.sessionId(), evenement.numeroSequence(), evenement.donnees());
    }

    // Rattrapage des transcriptions perdues : un chunk deja recu et durable en
    // base peut ne jamais avoir ete transcrit si le serveur redemarre entre la
    // sauvegarde du chunk et l'execution du listener @Async ci-dessus (voir
    // docs/phases/phase-12-resilience-capture.md). Le delai de grace evite de
    // retraiter un chunk encore en cours de traitement normal (timeout Azure
    // Speech documente a 90s, marge large a 120s par defaut).
    @Scheduled(cron = "${memoria.transcription.rattrapage.cron:0 */5 * * * *}")
    public void rattraperTranscriptionsManquantes() {
        Instant avant = Instant.now().minusSeconds(delaiGraceRattrapageSecondes);
        List<AudioChunk> chunksSansTranscription = audioChunkRepository.findChunksSansTranscriptionAvant(avant);
        for (AudioChunk chunk : chunksSansTranscription) {
            try {
                byte[] donnees = stockageAudio.lire(chunk.getCheminStockage());
                traiterChunk(chunk.getSessionId(), chunk.getNumeroSequence(), donnees);
            } catch (Exception e) {
                LOG.warn("Echec du rattrapage de la transcription du chunk {} de la session {}", chunk.getNumeroSequence(), chunk.getSessionId(), e);
            }
        }
    }

    private void traiterChunk(UUID sessionId, int numeroSequence, byte[] donnees) {
        try {
            ResultatTranscription resultat = transcripteur.transcrire(donnees);
            enregistrer(sessionId, numeroSequence, TranscriptionStatut.REUSSIE, resultat.texteComplet(), resultat.segments());
        } catch (Exception e) {
            LOG.warn("Echec de la transcription du chunk {} de la session {}", numeroSequence, sessionId, e);
            enregistrer(sessionId, numeroSequence, TranscriptionStatut.ECHEC, null, List.of());
        }
        signalerSiToutesLesTranscriptionsSontTerminees(sessionId);
    }

    private void enregistrer(
            UUID sessionId,
            int numeroSequence,
            TranscriptionStatut statut,
            String texte,
            List<SegmentLocuteur> segments
    ) {
        Transcription transcription = new Transcription(sessionId, numeroSequence, texte, statut, segments);
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

    public List<TranscriptionResponse> obtenirTranscriptionsAvecIdentification(UUID sessionId) {
        List<Transcription> transcriptions = obtenirTranscriptions(sessionId);

        Set<UUID> utilisateurIds = transcriptions.stream()
                .flatMap(t -> t.getSegmentsLocuteur().stream())
                .map(SegmentLocuteur::getUtilisateurIdentifieId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> noms = utilisateurRepository.findAllById(utilisateurIds).stream()
                .collect(Collectors.toMap(Utilisateur::getId, Utilisateur::nomAffichage));

        return transcriptions.stream().map(t -> TranscriptionResponse.depuis(t, noms)).toList();
    }
}
