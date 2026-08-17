package com.memoria.ecole.exercice;

import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;
import com.memoria.ecole.qcm.StatutQcm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final ExerciceMatiereNotionRepository exerciceMatiereNotionRepository;
    private final MatiereService matiereService;
    private final NotionService notionService;
    private final AgregateurContenuMatiereService agregateurContenuMatiereService;
    private final GenerateurExerciceSaisieLibrePort generateurExercice;
    private final TentativeExerciceSaisieLibreRepository tentativeRepository;

    public ExerciceSaisieLibreService(
            ExerciceMatiereRepository exerciceMatiereRepository,
            ExerciceMatiereNotionRepository exerciceMatiereNotionRepository,
            MatiereService matiereService,
            NotionService notionService,
            AgregateurContenuMatiereService agregateurContenuMatiereService,
            GenerateurExerciceSaisieLibrePort generateurExercice,
            TentativeExerciceSaisieLibreRepository tentativeRepository
    ) {
        this.exerciceMatiereRepository = exerciceMatiereRepository;
        this.exerciceMatiereNotionRepository = exerciceMatiereNotionRepository;
        this.matiereService = matiereService;
        this.notionService = notionService;
        this.agregateurContenuMatiereService = agregateurContenuMatiereService;
        this.generateurExercice = generateurExercice;
        this.tentativeRepository = tentativeRepository;
    }

    // Genere a la demande, mis en cache tant que la selection de notions ne
    // change pas -- meme doctrine que QcmMatiereService.obtenirOuGenererQcmMatiere
    // (un seul ExerciceMatiere persiste par matiere, remplace si la selection
    // demandee differe de celle deja en base).
    @Transactional
    public ExerciceMatiere obtenirOuGenererExercices(UUID matiereId, Set<UUID> notionIds, UUID utilisateurId) {
        matiereService.verifierMembreDuCouloir(matiereId, utilisateurId);

        Optional<ExerciceMatiere> existant = exerciceMatiereRepository.findByMatiereId(matiereId);
        // Un ECHEC precedent n'est jamais reutilise comme cache : sinon un
        // essai transitoirement rate (Azure OpenAI indisponible) bloquerait
        // definitivement toute nouvelle tentative sur la meme selection.
        if (existant.isPresent() && existant.get().getStatut() == StatutQcm.REUSSI && memeSelection(existant.get().getId(), notionIds)) {
            return existant.get();
        }
        existant.ifPresent(exercice -> {
            exerciceMatiereNotionRepository.deleteByExerciceMatiereId(exercice.getId());
            exerciceMatiereRepository.deleteById(exercice.getId());
            // Sans flush explicite, Hibernate execute les insertions avant les
            // suppressions (ordre de flush standard JPA) : la nouvelle ligne
            // violerait la contrainte unique sur matiereId tant que l'ancienne
            // n'a pas ete physiquement supprimee.
            exerciceMatiereRepository.flush();
        });

        // Notions selectionnees listees explicitement pour que chacune soit
        // couverte par au moins une question -- voir
        // GenerateurExerciceSaisieLibreAzureOpenAI.CONSIGNE_GENERATION, nombre
        // de questions jamais fixe.
        String contenu = construireContenuAvecNotions(matiereId, notionIds);
        if (contenu.isBlank()) {
            throw new AucunContenuDisponiblePourExerciceException(matiereId);
        }

        try {
            ExercicesGeneres genere = generateurExercice.genererExercices(contenu);
            List<QuestionSaisieLibre> questions = genere.questions().stream()
                    .map(question -> new QuestionSaisieLibre(question.enonce(), question.elementsAttendus()))
                    .toList();
            return enregistrer(matiereId, questions, StatutQcm.REUSSI, notionIds);
        } catch (Exception e) {
            LOG.warn("Echec de la generation des exercices pour la matiere {}", matiereId, e);
            return enregistrer(matiereId, List.of(), StatutQcm.ECHEC, notionIds);
        }
    }

    private boolean memeSelection(UUID exerciceMatiereId, Set<UUID> notionIds) {
        Set<UUID> selectionExistante = exerciceMatiereNotionRepository.findByExerciceMatiereId(exerciceMatiereId).stream()
                .map(ExerciceMatiereNotion::getNotionId)
                .collect(Collectors.toSet());
        return selectionExistante.equals(notionIds);
    }

    private String construireContenuAvecNotions(UUID matiereId, Set<UUID> notionIds) {
        List<Notion> notions = notionService.listerNotionsParMatiere(matiereId).stream()
                .filter(notion -> notionIds.contains(notion.getId()))
                .toList();
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

    private ExerciceMatiere enregistrer(UUID matiereId, List<QuestionSaisieLibre> questions, StatutQcm statut, Set<UUID> notionIds) {
        ExerciceMatiere exercice = exerciceMatiereRepository.save(new ExerciceMatiere(matiereId, questions, statut));
        notionIds.forEach(notionId -> exerciceMatiereNotionRepository.save(new ExerciceMatiereNotion(exercice.getId(), notionId)));
        return exercice;
    }

    public ExerciceMatiere obtenirExercices(UUID matiereId) {
        return exerciceMatiereRepository.findByMatiereId(matiereId)
                .orElseThrow(() -> new ExerciceMatiereNotFoundException(matiereId));
    }

    // Notions couvertes par l'exercice actuellement persiste -- sert au
    // frontend a precocher la selection correspondant au contenu deja
    // affiche.
    public List<UUID> listerNotionIdsCouvertes(UUID exerciceMatiereId) {
        return exerciceMatiereNotionRepository.findByExerciceMatiereId(exerciceMatiereId).stream()
                .map(ExerciceMatiereNotion::getNotionId)
                .toList();
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
