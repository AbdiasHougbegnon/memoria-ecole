package com.memoria.ecole.exercice;

import com.memoria.core.document.ExtracteurDocumentPort;
import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.StockageDocumentPort;
import com.memoria.core.document.TypeDocument;
import com.memoria.ecole.matiere.MatiereService;

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

// Miroir de DocumentMatiereService (upload synchrone + event + listener
// @Async qui relit le fichier et extrait le texte), sans generation de
// notions candidates -- un travail papier d'etudiant n'est pas du contenu de
// cours a proposer a toute la classe. Ouvert a tout membre du couloir
// (contrairement a DocumentMatiereService, reserve au proprietaire) :
// soumettre son propre travail n'est pas modifier le contenu pedagogique.
//
// Le meme listener enchaine aussi la correction automatique (CorrecteurTravailPapierPort)
// juste apres l'extraction : avant cet increment, le travail n'etait que
// transcrit et stocke, jamais analyse/corrige, ce qui ne repondait pas au
// besoin reel de l'etudiant (voir docs/phases/phase-24-correction-travail-papier-navigation.md).
@Service
public class TravailPapierService {

    private static final Logger LOG = LoggerFactory.getLogger(TravailPapierService.class);

    private final TravailPapierMatiereRepository travailPapierRepository;
    private final StockageDocumentPort stockageDocument;
    private final ExtracteurDocumentPort extracteurDocument;
    private final CorrecteurTravailPapierPort correcteurTravailPapier;
    private final MatiereService matiereService;
    private final ApplicationEventPublisher eventPublisher;

    public TravailPapierService(
            TravailPapierMatiereRepository travailPapierRepository,
            StockageDocumentPort stockageDocument,
            ExtracteurDocumentPort extracteurDocument,
            CorrecteurTravailPapierPort correcteurTravailPapier,
            MatiereService matiereService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.travailPapierRepository = travailPapierRepository;
        this.stockageDocument = stockageDocument;
        this.extracteurDocument = extracteurDocument;
        this.correcteurTravailPapier = correcteurTravailPapier;
        this.matiereService = matiereService;
        this.eventPublisher = eventPublisher;
    }

    public TravailPapierMatiere soumettre(
            UUID matiereId, String nomFichierOriginal, String typeContenu, byte[] contenu, UUID utilisateurId
    ) {
        matiereService.verifierMembreDuCouloir(matiereId, utilisateurId);

        String nomFichier = (nomFichierOriginal == null || nomFichierOriginal.isBlank())
                ? "travail-papier"
                : nomFichierOriginal;
        TypeDocument type = "application/pdf".equalsIgnoreCase(typeContenu) ? TypeDocument.PDF : TypeDocument.PHOTO;

        String chemin = stockageDocument.sauvegarder(matiereId, nomFichier, contenu);
        TravailPapierMatiere travail = new TravailPapierMatiere(matiereId, utilisateurId, type, nomFichier, chemin);
        TravailPapierMatiere sauvegarde = travailPapierRepository.save(travail);

        eventPublisher.publishEvent(new TravailPapierTeleverseEvent(sauvegarde.getId()));
        return sauvegarde;
    }

    @Async
    @EventListener
    public void surTravailPapierTeleverse(TravailPapierTeleverseEvent evenement) {
        TravailPapierMatiere travail = travailPapierRepository.findById(evenement.travailPapierId()).orElse(null);
        if (travail == null) {
            return;
        }

        String texte;
        try {
            byte[] contenu = Files.readAllBytes(Path.of(travail.getCheminStockage()));
            texte = extracteurDocument.extraireTexte(contenu);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Echec de l'extraction du travail papier {}", travail.getId(), e);
            travail.marquerEchec();
            travailPapierRepository.save(travail);
            return;
        }

        travail.marquerReussi(texte);
        tenterCorrection(travail);
        travailPapierRepository.save(travail);
    }

    // L'extraction a reussi : le texte reste consultable et discutable avec
    // le tuteur meme si la correction automatique echoue -- meme doctrine
    // degradee que ExerciceSaisieLibreService.soumettreReponses (une panne
    // Azure OpenAI ne doit pas faire perdre le travail deja extrait).
    private void tenterCorrection(TravailPapierMatiere travail) {
        try {
            CorrectionTravailPapier correction = correcteurTravailPapier.corriger(travail.getTexteExtrait());
            travail.enregistrerCorrection(correction.niveau(), correction.correction());
        } catch (RuntimeException e) {
            LOG.warn("Echec de la correction du travail papier {}", travail.getId(), e);
        }
    }

    public List<TravailPapierMatiere> listerMesTravaux(UUID matiereId, UUID utilisateurId) {
        return travailPapierRepository.findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(matiereId, utilisateurId);
    }

    // Reessai manuel pour les travaux soumis avant l'ajout de la correction
    // automatique (phase 24), qui restent sans correction pour toujours sinon
    // -- voir docs/phases/phase-26-reessai-correction-travail-papier.md.
    // Si la premiere tentative avait deja echoue (Azure indisponible), ce
    // reessai relance simplement tenterCorrection sur le texte deja extrait,
    // sans re-televerser ni re-extraire.
    public TravailPapierMatiere reessayerCorrection(UUID matiereId, UUID travailId, UUID utilisateurId) {
        TravailPapierMatiere travail = travailPapierRepository.findById(travailId)
                .orElseThrow(() -> new TravailPapierMatiereNotFoundException(travailId));
        if (!travail.getMatiereId().equals(matiereId) || !travail.getUtilisateurId().equals(utilisateurId)) {
            throw new AccesTravailPapierRefuseException(travailId, utilisateurId);
        }
        if (travail.getStatut() != StatutDocument.REUSSI || travail.getTexteExtrait() == null || travail.getTexteExtrait().isBlank()) {
            throw new TexteExtraitIndisponibleException(travailId);
        }
        tenterCorrection(travail);
        return travailPapierRepository.save(travail);
    }
}
