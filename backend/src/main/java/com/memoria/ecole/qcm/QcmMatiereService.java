package com.memoria.ecole.qcm;

import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// QCM de revision progressive sur toute la matiere (phase 22c) -- a cote du
// QCM par session (QcmService) qui reste utile pour reviser un cours precis.
// Reutilise le meme port de generation (GenerateurQcmPort) et le meme
// TentativeQcm/TentativeQcmRepository (qcmId n'est pas une FK JPA, fonctionne
// indifferemment pour un Qcm ou un QcmMatiere).
@Service
public class QcmMatiereService {

    private static final Logger LOG = LoggerFactory.getLogger(QcmMatiereService.class);

    private final QcmMatiereRepository qcmMatiereRepository;
    private final MatiereService matiereService;
    private final NotionService notionService;
    private final AgregateurContenuMatiereService agregateurContenuMatiereService;
    private final GenerateurQcmPort generateurQcm;
    private final TentativeQcmRepository tentativeQcmRepository;

    public QcmMatiereService(
            QcmMatiereRepository qcmMatiereRepository,
            MatiereService matiereService,
            NotionService notionService,
            AgregateurContenuMatiereService agregateurContenuMatiereService,
            GenerateurQcmPort generateurQcm,
            TentativeQcmRepository tentativeQcmRepository
    ) {
        this.qcmMatiereRepository = qcmMatiereRepository;
        this.matiereService = matiereService;
        this.notionService = notionService;
        this.agregateurContenuMatiereService = agregateurContenuMatiereService;
        this.generateurQcm = generateurQcm;
        this.tentativeQcmRepository = tentativeQcmRepository;
    }

    // Genere a la demande, mis en cache des la premiere generation -- comme
    // le QCM par session. Pas de regeneration automatique quand du contenu
    // est ajoute a la matiere ensuite (un nouvel appel explicite est requis).
    public QcmMatiere obtenirOuGenererQcmMatiere(UUID matiereId, UUID utilisateurId) {
        matiereService.verifierMembreDuCouloir(matiereId, utilisateurId);

        Optional<QcmMatiere> existant = qcmMatiereRepository.findByMatiereId(matiereId);
        if (existant.isPresent()) {
            return existant.get();
        }

        // Les notions validees sont listees explicitement (pas seulement
        // noyees dans les resumes agreges) pour que le nombre de questions
        // generees suive la richesse reelle du programme -- voir
        // GenerateurQcmAzureOpenAI.CONSIGNE, plus de nombre fixe de questions.
        String contenu = construireContenuAvecNotions(matiereId);
        if (contenu.isBlank()) {
            throw new AucunContenuMatiereDisponibleException(matiereId);
        }

        try {
            QcmGenere genere = generateurQcm.genererQcm(contenu);
            List<QuestionQcm> questions = genere.questions().stream()
                    .map(question -> new QuestionQcm(
                            question.enonce(),
                            question.choix().get(0),
                            question.choix().get(1),
                            question.choix().get(2),
                            question.choix().get(3),
                            question.indexReponseCorrecte(),
                            question.explication()
                    ))
                    .toList();
            return enregistrerSiAbsent(matiereId, questions, StatutQcm.REUSSI);
        } catch (Exception e) {
            LOG.warn("Echec de la generation du QCM de matiere pour la matiere {}", matiereId, e);
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

    private QcmMatiere enregistrerSiAbsent(UUID matiereId, List<QuestionQcm> questions, StatutQcm statut) {
        Optional<QcmMatiere> existant = qcmMatiereRepository.findByMatiereId(matiereId);
        if (existant.isPresent()) {
            // Une execution concurrente a deja cree ce QCM.
            return existant.get();
        }
        return qcmMatiereRepository.save(new QcmMatiere(matiereId, questions, statut));
    }

    public QcmMatiere obtenirQcmMatiere(UUID matiereId) {
        return qcmMatiereRepository.findByMatiereId(matiereId)
                .orElseThrow(() -> new QcmMatiereNotFoundException(matiereId));
    }

    public TentativeQcm soumettreTentative(UUID matiereId, UUID utilisateurId, List<Integer> reponses) {
        QcmMatiere qcm = obtenirQcmMatiere(matiereId);
        List<QuestionQcm> questions = qcm.getQuestions();

        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            Integer reponse = i < reponses.size() ? reponses.get(i) : null;
            if (reponse != null && reponse == questions.get(i).getIndexReponseCorrecte()) {
                score++;
            }
        }

        TentativeQcm tentative = tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId)
                .orElseGet(() -> new TentativeQcm(qcm.getId(), utilisateurId));
        tentative.enregistrerReponses(reponses, score, questions.size());
        return tentativeQcmRepository.save(tentative);
    }

    public Optional<TentativeQcm> obtenirMaTentative(UUID matiereId, UUID utilisateurId) {
        QcmMatiere qcm = obtenirQcmMatiere(matiereId);
        return tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId);
    }
}
