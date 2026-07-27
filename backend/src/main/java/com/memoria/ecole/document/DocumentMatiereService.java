package com.memoria.ecole.document;

import com.memoria.core.couloir.CouloirService;
import com.memoria.core.document.ExtracteurDocumentPort;
import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.core.document.TypeDocument;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.NotionCandidate;
import com.memoria.ecole.notion.NotionCandidateRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

// Miroir de com.memoria.core.document.DocumentService (upload synchrone +
// event + listener @Async qui relit le fichier et extrait le texte), avec en
// plus la generation de notions candidates -- brique de la phase 18. Seul le
// proprietaire du couloir (l'enseignant) peut televerser une fiche, comme
// pour MatiereService/NotionService.
@Service
public class DocumentMatiereService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentMatiereService.class);

    private final DocumentMatiereRepository documentMatiereRepository;
    private final NotionCandidateRepository notionCandidateRepository;
    private final StockageDocumentPort stockageDocument;
    private final ExtracteurDocumentPort extracteurDocument;
    private final GenerateurNotionsDepuisDocumentPort generateurNotions;
    private final MatiereService matiereService;
    private final CouloirService couloirService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentMatiereService(
            DocumentMatiereRepository documentMatiereRepository,
            NotionCandidateRepository notionCandidateRepository,
            StockageDocumentPort stockageDocument,
            ExtracteurDocumentPort extracteurDocument,
            GenerateurNotionsDepuisDocumentPort generateurNotions,
            MatiereService matiereService,
            CouloirService couloirService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.documentMatiereRepository = documentMatiereRepository;
        this.notionCandidateRepository = notionCandidateRepository;
        this.stockageDocument = stockageDocument;
        this.extracteurDocument = extracteurDocument;
        this.generateurNotions = generateurNotions;
        this.matiereService = matiereService;
        this.couloirService = couloirService;
        this.eventPublisher = eventPublisher;
    }

    public DocumentMatiere televerser(
            UUID matiereId, String nomFichierOriginal, String typeContenu, byte[] contenu, UUID utilisateurId
    ) {
        Matiere matiere = matiereService.obtenirMatiere(matiereId);
        couloirService.verifierProprietaireDuCouloir(matiere.getCouloirId(), utilisateurId);

        String nomFichier = (nomFichierOriginal == null || nomFichierOriginal.isBlank())
                ? "document"
                : nomFichierOriginal;
        TypeDocument type = determinerType(nomFichier, typeContenu);

        String chemin = stockageDocument.sauvegarder(matiereId, nomFichier, contenu);
        DocumentMatiere document = new DocumentMatiere(matiereId, type, nomFichier, chemin);
        DocumentMatiere sauvegarde = documentMatiereRepository.save(document);

        eventPublisher.publishEvent(new DocumentMatiereTeleverseEvent(sauvegarde.getId()));
        return sauvegarde;
    }

    private TypeDocument determinerType(String nomFichier, String typeContenu) {
        if (typeContenu != null && typeContenu.equalsIgnoreCase("application/pdf")) {
            return TypeDocument.PDF;
        }
        if (nomFichier.toLowerCase().endsWith(".pdf")) {
            return TypeDocument.PDF;
        }
        return TypeDocument.PHOTO;
    }

    @Async
    @EventListener
    public void surDocumentTeleverse(DocumentMatiereTeleverseEvent evenement) {
        DocumentMatiere document = documentMatiereRepository.findById(evenement.documentMatiereId()).orElse(null);
        if (document == null) {
            return;
        }

        try {
            byte[] contenu = Files.readAllBytes(Path.of(document.getCheminStockage()));
            String texte = extracteurDocument.extraireTexte(contenu);
            document.marquerReussi(texte);
            documentMatiereRepository.save(document);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Echec de l'extraction du document de matiere {}", document.getId(), e);
            document.marquerEchec();
            documentMatiereRepository.save(document);
            return;
        }

        genererCandidats(document);
    }

    // Panne isolee de la generation de notions candidates : le document reste
    // marque REUSSI (l'extraction, elle, a bien fonctionne) mais aucun
    // candidat n'est cree -- pas de nouvelle tentative automatique, comme
    // ResumeCoursService qui ne relance pas seul un echec de generation IA.
    private void genererCandidats(DocumentMatiere document) {
        try {
            List<GenerateurNotionsDepuisDocumentPort.CandidatNotionGenere> candidats =
                    generateurNotions.genererNotionsCandidates(document.getTexteExtrait());
            candidats.forEach(candidat -> notionCandidateRepository.save(new NotionCandidate(
                    document.getId(), document.getMatiereId(), candidat.terme(), candidat.definition()
            )));
        } catch (RuntimeException e) {
            LOG.warn("Echec de la generation de notions candidates pour le document {}", document.getId(), e);
        }
    }

    public List<DocumentMatiere> listerDocuments(UUID matiereId) {
        matiereService.obtenirMatiere(matiereId);
        return documentMatiereRepository.findByMatiereIdOrderByDateCreationAsc(matiereId);
    }
}
