package com.memoria.core.gouvernance;

import com.memoria.core.audio.AudioChunkRepository;
import com.memoria.core.audio.StockageAudioPort;
import com.memoria.core.document.DocumentRepository;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.core.filmemoire.FilMemoire;
import com.memoria.core.filmemoire.FilMemoireRepository;
import com.memoria.core.recherche.IndexRechercheRepository;
import com.memoria.core.recherche.RecherchePort;
import com.memoria.core.resume.ResumeRepository;
import com.memoria.core.session.SessionRepository;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.engagement.EngagementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Point d'entree unique pour "supprimer completement une session et tout ce
// qui en derive" -- reutilise par l'effacement de compte (GouvernanceDonneesService,
// sessions personnelles) et par la purge de retention (RetentionService,
// toutes les sessions, sans distinction personnelle/partagee). Voir
// docs/phases/phase-13-gouvernance-donnees.md.
@Service
public class SessionPurgeService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionPurgeService.class);

    private final DocumentRepository documentRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final ResumeRepository resumeRepository;
    private final CompteRenduRepository compteRenduRepository;
    private final ResumeCoursRepository resumeCoursRepository;
    private final IndexRechercheRepository indexRechercheRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final EngagementRepository engagementRepository;
    private final FilMemoireRepository filMemoireRepository;
    private final SessionRepository sessionRepository;
    private final StockageAudioPort stockageAudio;
    private final StockageDocumentPort stockageDocument;
    private final RecherchePort recherche;

    public SessionPurgeService(
            DocumentRepository documentRepository,
            TranscriptionRepository transcriptionRepository,
            ResumeRepository resumeRepository,
            CompteRenduRepository compteRenduRepository,
            ResumeCoursRepository resumeCoursRepository,
            IndexRechercheRepository indexRechercheRepository,
            AudioChunkRepository audioChunkRepository,
            EngagementRepository engagementRepository,
            FilMemoireRepository filMemoireRepository,
            SessionRepository sessionRepository,
            StockageAudioPort stockageAudio,
            StockageDocumentPort stockageDocument,
            RecherchePort recherche
    ) {
        this.documentRepository = documentRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.resumeRepository = resumeRepository;
        this.compteRenduRepository = compteRenduRepository;
        this.resumeCoursRepository = resumeCoursRepository;
        this.indexRechercheRepository = indexRechercheRepository;
        this.audioChunkRepository = audioChunkRepository;
        this.engagementRepository = engagementRepository;
        this.filMemoireRepository = filMemoireRepository;
        this.sessionRepository = sessionRepository;
        this.stockageAudio = stockageAudio;
        this.stockageDocument = stockageDocument;
        this.recherche = recherche;
    }

    @Transactional
    public void purgerSessionCompletement(UUID sessionId) {
        documentRepository.deleteBySessionId(sessionId);
        transcriptionRepository.deleteBySessionId(sessionId);
        resumeRepository.deleteBySessionId(sessionId);
        compteRenduRepository.deleteBySessionId(sessionId);
        resumeCoursRepository.deleteBySessionId(sessionId);
        indexRechercheRepository.deleteBySessionId(sessionId);
        audioChunkRepository.deleteBySessionId(sessionId);
        engagementRepository.deleteBySessionId(sessionId);
        filMemoireRepository.findBySessionId(sessionId).ifPresent(fil -> retirerOuSupprimerFil(fil, sessionId));
        sessionRepository.deleteById(sessionId);
    }

    // Best-effort, jamais transactionnel avec purgerSessionCompletement (doctrine
    // EmpreinteVocaleService.revoquer) : appele separement par l'appelant APRES
    // purgerSessionCompletement, jamais depuis une methode de ce meme service
    // (l'auto-invocation Spring ignorerait silencieusement le @Transactional
    // ci-dessus).
    public void nettoyerDependancesExternes(UUID sessionId) {
        try {
            stockageAudio.supprimerSession(sessionId);
        } catch (Exception e) {
            LOG.warn("Echec de la suppression des chunks audio de la session {}", sessionId, e);
        }
        try {
            stockageDocument.supprimerSession(sessionId);
        } catch (Exception e) {
            LOG.warn("Echec de la suppression des documents de la session {}", sessionId, e);
        }
        try {
            recherche.supprimerDocumentsSession(sessionId);
        } catch (Exception e) {
            LOG.warn("Echec de la suppression des documents Azure AI Search de la session {}", sessionId, e);
        }
    }

    private void retirerOuSupprimerFil(FilMemoire fil, UUID sessionId) {
        fil.retirerSession(sessionId);
        if (fil.estVide()) {
            filMemoireRepository.delete(fil);
        } else {
            filMemoireRepository.save(fil);
        }
    }
}
