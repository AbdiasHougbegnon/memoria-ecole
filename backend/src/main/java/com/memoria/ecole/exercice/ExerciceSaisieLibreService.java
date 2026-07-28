package com.memoria.ecole.exercice;

import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;
import com.memoria.ecole.qcm.StatutQcm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// Exercices a reponse libre sur toute la matiere (phase 22d), a cote du QCM
// de matiere (QcmMatiereService) : questions ouvertes, notees qualitativement
// par l'IA plutot que par un score calcule localement -- chaque reponse
// declenche un veritable appel IA de correction, contrairement au QCM ou la
// correction est un simple calcul d'index.
@Service
public class ExerciceSaisieLibreService {

    private static final Logger LOG = LoggerFactory.getLogger(ExerciceSaisieLibreService.class);

    private final ExerciceMatiereRepository exerciceMatiereRepository;
    private final MatiereService matiereService;
    private final NotionService notionService;
    private final AgregateurContenuMatiereService agregateurContenuMatiereService;
    private final GenerateurExerciceSaisieLibrePort generateurExercice;
    private final TentativeExerciceSaisieLibreRepository tentativeRepository;

    public ExerciceSaisieLibreService(
            ExerciceMatiereRepository exerciceMatiereRepository,
            MatiereService matiereService,
            NotionService notionService,
            AgregateurContenuMatiereService agregateurContenuMatiereService,
            GenerateurExerciceSaisieLibrePort generateurExercice,
            TentativeExerciceSaisieLibreRepository tentativeRepository
    ) {
        this.exerciceMatiereRepository = exerciceMatiereRepository;
        this.matiereService = matiereService;
        this.notionService = notionService;
        this.agregateurContenuMatiereService = agregateurContenuMatiereService;
        this.generateurExercice = generateurExercice;
        this.tentativeRepository = tentativeRepository;
    }

    // Genere a la demande, mis en cache des la premiere generation -- meme
    // doctrine que le QCM de matiere.
    public ExerciceMatiere obtenirOuGenererExercices(UUID matiereId, UUID utilisateurId) {
        matiereService.verifierMembreDuCouloir(matiereId, utilisateurId);

        Optional<ExerciceMatiere> existant = exerciceMatiereRepository.findByMatiereId(matiereId);
        if (existant.isPresent()) {
            return existant.get();
        }

        // Notions listees explicitement pour que le nombre de questions
        // suive la richesse reelle du programme -- voir
        // GenerateurExerciceSaisieLibreAzureOpenAI.CONSIGNE_GENERATION, plus
        // de nombre fixe de questions.
        String contenu = construireContenuAvecNotions(matiereId);
        if (contenu.isBlank()) {
            throw new AucunContenuDisponiblePourExerciceException(matiereId);
        }

        try {
            ExercicesGeneres genere = generateurExercice.genererExercices(contenu);
            List<QuestionSaisieLibre> questions = genere.questions().stream()
                    .map(question -> new QuestionSaisieLibre(question.enonce(), question.elementsAttendus()))
                    .toList();
            return enregistrerSiAbsent(matiereId, questions, StatutQcm.REUSSI);
        } catch (Exception e) {
            LOG.warn("Echec de la generation des exercices pour la matiere {}", matiereId, e);
            return enregistrerSiAbsent(matiereId, List.of(), StatutQcm.ECHEC);
        }
    }

    private String construireContenuAvecNotions(UUID matiereId) {
        List<Notion> notions = notionService.listerNotionsParMatiere(matiereId);
        String contenuAgrege = agregateurContenuMatiereService.agregerContenu(matiereId);

        StringBuilder contenu = new StringBuilder();
        if (!notions.isEmpty()) {
            String listeNotions = notions.stream()
                    .map(notion -> "- " + notion.getTerme() + " : " + notion.getDefinition())
                    .collect(Collectors.joining("\n"));
            contenu.append("Notions au programme (a couvrir chacune par au moins une question) :\n")
                    .append(listeNotions).append("\n\n");
        }
        if (!contenuAgrege.isBlank()) {
            contenu.append("Contenu des cours et documents de la matiere :\n").append(contenuAgrege);
        }
        return contenu.toString();
    }

    private ExerciceMatiere enregistrerSiAbsent(UUID matiereId, List<QuestionSaisieLibre> questions, StatutQcm statut) {
        Optional<ExerciceMatiere> existant = exerciceMatiereRepository.findByMatiereId(matiereId);
        if (existant.isPresent()) {
            // Une execution concurrente a deja cree cet exercice.
            return existant.get();
        }
        return exerciceMatiereRepository.save(new ExerciceMatiere(matiereId, questions, statut));
    }

    public ExerciceMatiere obtenirExercices(UUID matiereId) {
        return exerciceMatiereRepository.findByMatiereId(matiereId)
                .orElseThrow(() -> new ExerciceMatiereNotFoundException(matiereId));
    }

    // Chaque reponse declenche un appel IA de correction independant : un
    // echec sur une question (Azure OpenAI indisponible) ne fait pas perdre
    // les reponses deja evaluees des autres questions.
    public TentativeExerciceSaisieLibre soumettreReponses(UUID matiereId, UUID utilisateurId, List<String> reponses) {
        ExerciceMatiere exercice = obtenirExercices(matiereId);
        List<QuestionSaisieLibre> questions = exercice.getQuestions();

        List<ReponseEvaluee> evaluees = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String reponse = i < reponses.size() && reponses.get(i) != null ? reponses.get(i) : "";
            QuestionSaisieLibre question = questions.get(i);
            evaluees.add(evaluer(question, reponse));
        }

        TentativeExerciceSaisieLibre tentative = tentativeRepository
                .findByExerciceMatiereIdAndUtilisateurId(exercice.getId(), utilisateurId)
                .orElseGet(() -> new TentativeExerciceSaisieLibre(exercice.getId(), utilisateurId));
        tentative.enregistrerReponses(evaluees);
        return tentativeRepository.save(tentative);
    }

    private ReponseEvaluee evaluer(QuestionSaisieLibre question, String reponse) {
        try {
            EvaluationReponseLibre evaluation = generateurExercice.evaluerReponse(
                    question.getEnonce(), question.getElementsAttendus(), reponse
            );
            return new ReponseEvaluee(reponse, evaluation.niveau(), evaluation.retour());
        } catch (Exception e) {
            LOG.warn("Echec de l'evaluation IA d'une reponse", e);
            return new ReponseEvaluee(reponse, null, "Evaluation indisponible pour le moment.");
        }
    }

    public Optional<TentativeExerciceSaisieLibre> obtenirMaTentative(UUID matiereId, UUID utilisateurId) {
        ExerciceMatiere exercice = obtenirExercices(matiereId);
        return tentativeRepository.findByExerciceMatiereIdAndUtilisateurId(exercice.getId(), utilisateurId);
    }
}
