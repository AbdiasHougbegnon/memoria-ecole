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
// @Async qui relit les fichiers et extrait le texte), sans generation de
// notions candidates -- un travail papier d'etudiant n'est pas du contenu de
// cours a proposer a toute la classe. Ouvert a tout membre du couloir
// (contrairement a DocumentMatiereService, reserve au proprietaire) :
// soumettre son propre travail n'est pas modifier le contenu pedagogique.
//
// Depuis la phase 28, deux fichiers sont soumis (enonce + reponse) plutot
// qu'un seul : le meme listener enchaine l'extraction des deux textes puis la
// correction (CorrecteurTravailPapierPort), qui decoupe desormais en
// exercices individuels (ExercicePapier) ancres sur l'enonce reel.
@Service
public class TravailPapierService {

    private static final Logger LOG = LoggerFactory.getLogger(TravailPapierService.class);

    private final TravailPapierMatiereRepository travailPapierRepository;
    private final ExercicePapierRepository exercicePapierRepository;
    private final StockageDocumentPort stockageDocument;
    private final ExtracteurDocumentPort extracteurDocument;
    private final CorrecteurTravailPapierPort correcteurTravailPapier;
    private final VerificateurComprehensionPort verificateurComprehension;
    private final MatiereService matiereService;
    private final ApplicationEventPublisher eventPublisher;

    public TravailPapierService(
            TravailPapierMatiereRepository travailPapierRepository,
            ExercicePapierRepository exercicePapierRepository,
            StockageDocumentPort stockageDocument,
            ExtracteurDocumentPort extracteurDocument,
            CorrecteurTravailPapierPort correcteurTravailPapier,
            VerificateurComprehensionPort verificateurComprehension,
            MatiereService matiereService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.travailPapierRepository = travailPapierRepository;
        this.exercicePapierRepository = exercicePapierRepository;
        this.stockageDocument = stockageDocument;
        this.extracteurDocument = extracteurDocument;
        this.correcteurTravailPapier = correcteurTravailPapier;
        this.verificateurComprehension = verificateurComprehension;
        this.matiereService = matiereService;
        this.eventPublisher = eventPublisher;
    }

    public TravailPapierMatiere soumettre(
            UUID matiereId,
            String nomFichierEnonceOriginal, String typeContenuEnonce, byte[] contenuEnonce,
            String nomFichierReponseOriginal, String typeContenuReponse, byte[] contenuReponse,
            UUID utilisateurId
    ) {
        matiereService.verifierMembreDuCouloir(matiereId, utilisateurId);

        String nomFichierEnonce = nomOuDefaut(nomFichierEnonceOriginal, "enonce");
        String nomFichierReponse = nomOuDefaut(nomFichierReponseOriginal, "reponse");
        TypeDocument typeEnonce = versTypeDocument(typeContenuEnonce);
        TypeDocument typeReponse = versTypeDocument(typeContenuReponse);

        String cheminEnonce = stockageDocument.sauvegarder(matiereId, nomFichierEnonce, contenuEnonce);
        String cheminReponse = stockageDocument.sauvegarder(matiereId, nomFichierReponse, contenuReponse);

        TravailPapierMatiere travail = new TravailPapierMatiere(
                matiereId, utilisateurId,
                typeEnonce, nomFichierEnonce, cheminEnonce,
                typeReponse, nomFichierReponse, cheminReponse
        );
        TravailPapierMatiere sauvegarde = travailPapierRepository.save(travail);

        eventPublisher.publishEvent(new TravailPapierTeleverseEvent(sauvegarde.getId()));
        return sauvegarde;
    }

    private String nomOuDefaut(String nomOriginal, String defaut) {
        return (nomOriginal == null || nomOriginal.isBlank()) ? defaut : nomOriginal;
    }

    private TypeDocument versTypeDocument(String typeContenu) {
        return "application/pdf".equalsIgnoreCase(typeContenu) ? TypeDocument.PDF : TypeDocument.PHOTO;
    }

    @Async
    @EventListener
    public void surTravailPapierTeleverse(TravailPapierTeleverseEvent evenement) {
        TravailPapierMatiere travail = travailPapierRepository.findById(evenement.travailPapierId()).orElse(null);
        if (travail == null) {
            return;
        }

        String texteEnonce;
        String texteReponse;
        try {
            texteEnonce = extracteurDocument.extraireTexte(Files.readAllBytes(Path.of(travail.getCheminStockageEnonce())));
            texteReponse = extracteurDocument.extraireTexte(Files.readAllBytes(Path.of(travail.getCheminStockageReponse())));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Echec de l'extraction du travail papier {}", travail.getId(), e);
            travail.marquerEchec();
            travailPapierRepository.save(travail);
            return;
        }

        travail.marquerReussi(texteEnonce, texteReponse);
        tenterCorrection(travail);
        travailPapierRepository.save(travail);
    }

    // L'extraction a reussi : les textes restent consultables et discutables
    // avec le tuteur meme si la correction automatique echoue -- meme
    // doctrine degradee que ExerciceSaisieLibreService.soumettreReponses (une
    // panne Azure OpenAI ne doit pas faire perdre le travail deja extrait).
    private void tenterCorrection(TravailPapierMatiere travail) {
        try {
            List<ExerciceCorrige> exercices = correcteurTravailPapier.corriger(
                    travail.getTexteExtraitEnonce(), travail.getTexteExtraitReponse()
            );
            exercicePapierRepository.deleteByTravailPapierId(travail.getId());
            for (int i = 0; i < exercices.size(); i++) {
                ExerciceCorrige exercice = exercices.get(i);
                exercicePapierRepository.save(new ExercicePapier(
                        travail.getId(), i, exercice.enonce(), exercice.reponseEtudiant(),
                        exercice.niveau(), exercice.syntheseCorrection(), exercice.points()
                ));
            }
        } catch (RuntimeException e) {
            LOG.warn("Echec de la correction du travail papier {}", travail.getId(), e);
        }
    }

    public List<TravailPapierMatiere> listerMesTravaux(UUID matiereId, UUID utilisateurId) {
        return travailPapierRepository.findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(matiereId, utilisateurId);
    }

    public List<ExercicePapier> listerExercices(UUID travailId) {
        return exercicePapierRepository.findByTravailPapierIdOrderByOrdreAsc(travailId);
    }

    // Reessai manuel pour les travaux dont la correction n'a pas encore
    // reussi (phase 26), ou dont l'enonce/la reponse ont ete extraits sans
    // que la correction en exercices n'ait encore eu lieu. Relance
    // simplement tenterCorrection sur les textes deja extraits, sans
    // re-televerser ni re-extraire.
    public TravailPapierMatiere reessayerCorrection(UUID matiereId, UUID travailId, UUID utilisateurId) {
        TravailPapierMatiere travail = travailPapierRepository.findById(travailId)
                .orElseThrow(() -> new TravailPapierMatiereNotFoundException(travailId));
        if (!travail.getMatiereId().equals(matiereId) || !travail.getUtilisateurId().equals(utilisateurId)) {
            throw new AccesTravailPapierRefuseException(travailId, utilisateurId);
        }
        if (travail.getStatut() != StatutDocument.REUSSI
                || travail.getTexteExtraitEnonce() == null || travail.getTexteExtraitEnonce().isBlank()
                || travail.getTexteExtraitReponse() == null || travail.getTexteExtraitReponse().isBlank()) {
            throw new TexteExtraitIndisponibleException(travailId);
        }
        tenterCorrection(travail);
        return travailPapierRepository.save(travail);
    }

    // Verification de comprehension (phase 30, brique C) : n'a de sens qu'en
    // mode progressif, une fois la correction disponible pour l'exercice.
    public ExercicePapier genererQuestionVerification(UUID matiereId, UUID travailId, UUID exerciceId, UUID utilisateurId) {
        ExercicePapier exercice = chargerExercice(matiereId, travailId, exerciceId, utilisateurId);
        if (exercice.getCorrectionSynthese() == null || exercice.getCorrectionSynthese().isBlank()) {
            throw new TexteExtraitIndisponibleException(travailId);
        }
        QuestionVerificationGeneree question = verificateurComprehension.genererQuestion(
                exercice.getEnonce(), exercice.getCorrectionSynthese(), exercice.getPointsCorrection()
        );
        exercice.enregistrerQuestionVerification(question.enonce(), question.choix());
        return exercicePapierRepository.save(exercice);
    }

    // Correction deterministe, sans appel IA : les cases cochees doivent
    // correspondre exactement aux choix marques corrects (ni manque, ni
    // choix incorrect coche).
    public ExercicePapier soumettreReponseChoix(
            UUID matiereId, UUID travailId, UUID exerciceId, UUID utilisateurId, List<Integer> indicesCoches
    ) {
        ExercicePapier exercice = chargerExercice(matiereId, travailId, exerciceId, utilisateurId);
        List<ChoixVerification> choix = exercice.getChoixVerification();
        boolean comprehensionValidee = true;
        for (int i = 0; i < choix.size(); i++) {
            boolean coche = indicesCoches.contains(i);
            if (coche != choix.get(i).isCorrect()) {
                comprehensionValidee = false;
                break;
            }
        }
        exercice.enregistrerResolutionVerification(comprehensionValidee);
        return exercicePapierRepository.save(exercice);
    }

    // Reponse libre (tapee ou dictee puis transcrite) : evaluation qualitative
    // par l'IA, meme classification NiveauMaitrise que le reste du projet.
    public ExercicePapier soumettreReponseLibre(
            UUID matiereId, UUID travailId, UUID exerciceId, UUID utilisateurId, String reponseTexte
    ) {
        ExercicePapier exercice = chargerExercice(matiereId, travailId, exerciceId, utilisateurId);
        var niveau = verificateurComprehension.evaluerReponseLibre(exercice.getQuestionVerificationEnonce(), reponseTexte);
        exercice.enregistrerResolutionVerification(niveau == com.memoria.ecole.notion.NiveauMaitrise.MAITRISEE);
        return exercicePapierRepository.save(exercice);
    }

    private ExercicePapier chargerExercice(UUID matiereId, UUID travailId, UUID exerciceId, UUID utilisateurId) {
        TravailPapierMatiere travail = travailPapierRepository.findById(travailId)
                .orElseThrow(() -> new TravailPapierMatiereNotFoundException(travailId));
        if (!travail.getMatiereId().equals(matiereId) || !travail.getUtilisateurId().equals(utilisateurId)) {
            throw new AccesTravailPapierRefuseException(travailId, utilisateurId);
        }
        ExercicePapier exercice = exercicePapierRepository.findById(exerciceId)
                .orElseThrow(() -> new ExercicePapierNotFoundException(exerciceId));
        if (!exercice.getTravailPapierId().equals(travailId)) {
            throw new ExercicePapierNotFoundException(exerciceId);
        }
        return exercice;
    }
}
