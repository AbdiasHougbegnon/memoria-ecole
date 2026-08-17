package com.memoria.ecole.qcm;

import com.memoria.ecole.matiere.AgregateurContenuMatiereService;
import com.memoria.ecole.matiere.MatiereService;
import com.memoria.ecole.notion.Notion;
import com.memoria.ecole.notion.NotionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final QcmMatiereNotionRepository qcmMatiereNotionRepository;
    private final MatiereService matiereService;
    private final NotionService notionService;
    private final AgregateurContenuMatiereService agregateurContenuMatiereService;
    private final GenerateurQcmPort generateurQcm;
    private final TentativeQcmRepository tentativeQcmRepository;

    public QcmMatiereService(
            QcmMatiereRepository qcmMatiereRepository,
            QcmMatiereNotionRepository qcmMatiereNotionRepository,
            MatiereService matiereService,
            NotionService notionService,
            AgregateurContenuMatiereService agregateurContenuMatiereService,
            GenerateurQcmPort generateurQcm,
            TentativeQcmRepository tentativeQcmRepository
    ) {
        this.qcmMatiereRepository = qcmMatiereRepository;
        this.qcmMatiereNotionRepository = qcmMatiereNotionRepository;
        this.matiereService = matiereService;
        this.notionService = notionService;
        this.agregateurContenuMatiereService = agregateurContenuMatiereService;
        this.generateurQcm = generateurQcm;
        this.tentativeQcmRepository = tentativeQcmRepository;
    }

    // Genere a la demande, mis en cache tant que la selection de notions ne
    // change pas -- un seul QcmMatiere persiste par matiere (contrainte
    // unique sur matiereId), mais son contenu est desormais lie a un
    // ensemble precis de notions (QcmMatiereNotion). Si la selection demandee
    // differe de celle du QCM deja en base, l'ancien est remplace par un
    // nouveau (regeneration), sinon il est reutilise tel quel (aucun appel
    // Azure OpenAI).
    @Transactional
    public QcmMatiere obtenirOuGenererQcmMatiere(UUID matiereId, Set<UUID> notionIds, UUID utilisateurId) {
        matiereService.verifierMembreDuCouloir(matiereId, utilisateurId);

        Optional<QcmMatiere> existant = qcmMatiereRepository.findByMatiereId(matiereId);
        // Un ECHEC precedent n'est jamais reutilise comme cache : sinon un
        // essai transitoirement rate (Azure OpenAI indisponible) bloquerait
        // definitivement toute nouvelle tentative sur la meme selection.
        if (existant.isPresent() && existant.get().getStatut() == StatutQcm.REUSSI && memeSelection(existant.get().getId(), notionIds)) {
            return existant.get();
        }
        existant.ifPresent(qcm -> {
            qcmMatiereNotionRepository.deleteByQcmMatiereId(qcm.getId());
            qcmMatiereRepository.deleteById(qcm.getId());
            // Sans flush explicite, Hibernate execute les insertions avant les
            // suppressions (ordre de flush standard JPA) : la nouvelle ligne
            // violerait la contrainte unique sur matiereId tant que l'ancienne
            // n'a pas ete physiquement supprimee.
            qcmMatiereRepository.flush();
        });

        // Les notions selectionnees sont listees explicitement (pas
        // seulement noyees dans les resumes agreges) pour que chacune soit
        // couverte par au moins une question -- voir
        // GenerateurQcmAzureOpenAI.CONSIGNE, nombre de questions jamais fixe.
        String contenu = construireContenuAvecNotions(matiereId, notionIds);
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
            return enregistrer(matiereId, questions, StatutQcm.REUSSI, notionIds);
        } catch (Exception e) {
            LOG.warn("Echec de la generation du QCM de matiere pour la matiere {}", matiereId, e);
            return enregistrer(matiereId, List.of(), StatutQcm.ECHEC, notionIds);
        }
    }

    private boolean memeSelection(UUID qcmMatiereId, Set<UUID> notionIds) {
        Set<UUID> selectionExistante = qcmMatiereNotionRepository.findByQcmMatiereId(qcmMatiereId).stream()
                .map(QcmMatiereNotion::getNotionId)
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

    private QcmMatiere enregistrer(UUID matiereId, List<QuestionQcm> questions, StatutQcm statut, Set<UUID> notionIds) {
        QcmMatiere qcm = qcmMatiereRepository.save(new QcmMatiere(matiereId, questions, statut));
        notionIds.forEach(notionId -> qcmMatiereNotionRepository.save(new QcmMatiereNotion(qcm.getId(), notionId)));
        return qcm;
    }

    public QcmMatiere obtenirQcmMatiere(UUID matiereId) {
        return qcmMatiereRepository.findByMatiereId(matiereId)
                .orElseThrow(() -> new QcmMatiereNotFoundException(matiereId));
    }

    // Notions couvertes par le QCM actuellement persiste -- sert au frontend
    // a precocher la selection correspondant au contenu deja affiche.
    public List<UUID> listerNotionIdsCouvertes(UUID qcmMatiereId) {
        return qcmMatiereNotionRepository.findByQcmMatiereId(qcmMatiereId).stream()
                .map(QcmMatiereNotion::getNotionId)
                .toList();
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
