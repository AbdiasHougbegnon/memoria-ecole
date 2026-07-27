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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Point d'entree unique pour "supprimer completement une session et tout ce
// qui en derive" -- reutilise par l'effacement de compte (GouvernanceDonneesService,
// sessions personnelles) et par la purge de retention (RetentionService,
// toutes les sessions, sans distinction personnelle/partagee). Voir
// docs/phases/phase-13-gouvernance-donnees.md.
//
// Les donnees specifiques a un produit (Ecole, Entreprise) sont purgees via
// PurgeurDonneesSessionPort, implemente par chaque produit et collecte
// automatiquement par Spring -- ce service core ne connait plus aucun type
// Ecole/Entreprise concret (voir audit du 2026-07-27, avant cette extraction
// il importait directement QcmRepository, CompteRenduRepository, etc.).
@Service
public class SessionPurgeService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionPurgeService.class);

    private final DocumentRepository documentRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final ResumeRepository resumeRepository;
    private final IndexRechercheRepository indexRechercheRepository;
    private final AudioChunkRepository audioChunkRepository;
    private final FilMemoireRepository filMemoireRepository;
    private final SessionRepository sessionRepository;
    private final StockageAudioPort stockageAudio;
    private final StockageDocumentPort stockageDocument;
    private final RecherchePort recherche;
    private final List<PurgeurDonneesSessionPort> purgeursProduits;

    public SessionPurgeService(
            DocumentRepository documentRepository,
            TranscriptionRepository transcriptionRepository,
            ResumeRepository resumeRepository,
            IndexRechercheRepository indexRechercheRepository,
            AudioChunkRepository audioChunkRepository,
            FilMemoireRepository filMemoireRepository,
            SessionRepository sessionRepository,
            StockageAudioPort stockageAudio,
            StockageDocumentPort stockageDocument,
            RecherchePort recherche,
            List<PurgeurDonneesSessionPort> purgeursProduits
    ) {
        this.documentRepository = documentRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.resumeRepository = resumeRepository;
        this.indexRechercheRepository = indexRechercheRepository;
        this.audioChunkRepository = audioChunkRepository;
        this.filMemoireRepository = filMemoireRepository;
        this.sessionRepository = sessionRepository;
        this.stockageAudio = stockageAudio;
        this.stockageDocument = stockageDocument;
        this.recherche = recherche;
        this.purgeursProduits = purgeursProduits;
    }

    @Transactional
    public void purgerSessionCompletement(UUID sessionId) {
        documentRepository.deleteBySessionId(sessionId);
        transcriptionRepository.deleteBySessionId(sessionId);
        resumeRepository.deleteBySessionId(sessionId);
        purgeursProduits.forEach(purgeur -> purgeur.purgerDonneesSession(sessionId));
        indexRechercheRepository.deleteBySessionId(sessionId);
        audioChunkRepository.deleteBySessionId(sessionId);
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
