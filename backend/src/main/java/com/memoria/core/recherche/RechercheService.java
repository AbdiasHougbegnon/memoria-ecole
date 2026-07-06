package com.memoria.core.recherche;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionStatus;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.SegmentLocuteur;
import com.memoria.core.transcription.ToutesTranscriptionsTermineesEvent;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RechercheService {

    private static final Logger LOG = LoggerFactory.getLogger(RechercheService.class);

    private final IndexRechercheRepository indexRechercheRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final SessionService sessionService;
    private final GenerateurEmbeddingPort generateurEmbedding;
    private final RecherchePort recherche;

    public RechercheService(
            IndexRechercheRepository indexRechercheRepository,
            TranscriptionRepository transcriptionRepository,
            SessionService sessionService,
            GenerateurEmbeddingPort generateurEmbedding,
            RecherchePort recherche
    ) {
        this.indexRechercheRepository = indexRechercheRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.sessionService = sessionService;
        this.generateurEmbedding = generateurEmbedding;
        this.recherche = recherche;
    }

    // Meme course connue que pour le resume (fin de session et derniere
    // transcription peuvent arriver dans n'importe quel ordre) : on tente
    // l'indexation dans les deux cas, avec une garde d'idempotence.
    @Async
    @EventListener
    public void surSessionTerminee(SessionTermineeEvent evenement) {
        indexerSiPossible(evenement.sessionId());
    }

    @Async
    @EventListener
    public void surToutesTranscriptionsTerminees(ToutesTranscriptionsTermineesEvent evenement) {
        indexerSiPossible(evenement.sessionId());
    }

    private void indexerSiPossible(UUID sessionId) {
        if (indexRechercheRepository.findBySessionId(sessionId).isPresent()) {
            return;
        }

        List<Transcription> transcriptionsReussies = transcriptionRepository
                .findBySessionIdOrderByNumeroSequenceAsc(sessionId).stream()
                .filter(transcription -> transcription.getStatut() == TranscriptionStatut.REUSSIE)
                .toList();
        if (transcriptionsReussies.isEmpty()) {
            return;
        }

        List<SegmentARecherche> segments = transcriptionsReussies.stream()
                .flatMap(transcription -> transcription.getSegmentsLocuteur().stream()
                        .filter(segment -> segment.getTexte() != null && !segment.getTexte().isBlank())
                        .map(segment -> versSegmentARecherche(transcription.getNumeroSequence(), segment)))
                .toList();
        if (segments.isEmpty()) {
            return;
        }

        Session session = sessionService.obtenirSession(sessionId);

        try {
            List<float[]> vecteurs = generateurEmbedding.genererEmbeddings(
                    segments.stream().map(SegmentARecherche::texte).toList()
            );
            recherche.indexerSegments(sessionId, session.getTitre(), session.getDateCreation(), segments, vecteurs);
            enregistrerSiAbsent(sessionId, segments.size(), StatutIndexRecherche.REUSSI);
        } catch (Exception e) {
            LOG.warn("Echec de l'indexation semantique de la session {}", sessionId, e);
            enregistrerSiAbsent(sessionId, segments.size(), StatutIndexRecherche.ECHEC);
        }
    }

    private SegmentARecherche versSegmentARecherche(int numeroSequence, SegmentLocuteur segment) {
        return new SegmentARecherche(
                numeroSequence,
                segment.getLocuteur(),
                segment.getTexte(),
                segment.getOffsetMillisecondes(),
                segment.getDureeMillisecondes()
        );
    }

    private void enregistrerSiAbsent(UUID sessionId, int nombreSegments, StatutIndexRecherche statut) {
        if (indexRechercheRepository.findBySessionId(sessionId).isPresent()) {
            // Une execution concurrente a deja indexe cette session.
            return;
        }
        indexRechercheRepository.save(new IndexRecherche(sessionId, nombreSegments, statut));
    }

    public List<ResultatRecherche> rechercher(String requete, int limite) {
        List<float[]> vecteurs = generateurEmbedding.genererEmbeddings(List.of(requete));
        return recherche.rechercher(requete, vecteurs.get(0), limite);
    }

    // Rattrapage pour les sessions terminees avant l'existence de cette
    // fonctionnalite (jamais passees par surSessionTerminee /
    // surToutesTranscriptionsTerminees). S'appuie sur la meme garde
    // d'idempotence : ne retraite jamais une session deja indexee, donc sans
    // risque a relancer plusieurs fois.
    @Async
    public void reindexerHistorique() {
        sessionService.listerSessions().stream()
                .filter(session -> session.getStatut() == SessionStatus.TERMINEE)
                .forEach(session -> indexerSiPossible(session.getId()));
    }
}
