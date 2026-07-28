package com.memoria.ecole.exercice;

import com.memoria.core.document.ExtracteurDocumentPort;
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
@Service
public class TravailPapierService {

    private static final Logger LOG = LoggerFactory.getLogger(TravailPapierService.class);

    private final TravailPapierMatiereRepository travailPapierRepository;
    private final StockageDocumentPort stockageDocument;
    private final ExtracteurDocumentPort extracteurDocument;
    private final MatiereService matiereService;
    private final ApplicationEventPublisher eventPublisher;

    public TravailPapierService(
            TravailPapierMatiereRepository travailPapierRepository,
            StockageDocumentPort stockageDocument,
            ExtracteurDocumentPort extracteurDocument,
            MatiereService matiereService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.travailPapierRepository = travailPapierRepository;
        this.stockageDocument = stockageDocument;
        this.extracteurDocument = extracteurDocument;
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

        try {
            byte[] contenu = Files.readAllBytes(Path.of(travail.getCheminStockage()));
            String texte = extracteurDocument.extraireTexte(contenu);
            travail.marquerReussi(texte);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Echec de l'extraction du travail papier {}", travail.getId(), e);
            travail.marquerEchec();
        }
        travailPapierRepository.save(travail);
    }

    public List<TravailPapierMatiere> listerMesTravaux(UUID matiereId, UUID utilisateurId) {
        return travailPapierRepository.findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(matiereId, utilisateurId);
    }
}
