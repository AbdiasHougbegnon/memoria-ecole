package com.memoria.ecole.qcm;

import com.memoria.core.session.SessionService;
import com.memoria.ecole.resumecours.NotionCours;
import com.memoria.ecole.resumecours.ResumeCours;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.resumecours.StatutResumeCours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// S'appuie sur le resume de cours deja genere (synthese + notions), pas sur la
// transcription brute -- reutilise un resultat existant plutot que de repayer
// un appel Azure OpenAI sur le texte complet (discipline de couts). Voir
// docs/phases/phase-15-qcm-revision.md.
@Service
public class QcmService {

    private static final Logger LOG = LoggerFactory.getLogger(QcmService.class);

    private final QcmRepository qcmRepository;
    private final ResumeCoursRepository resumeCoursRepository;
    private final GenerateurQcmPort generateurQcm;
    private final SessionService sessionService;
    private final TentativeQcmRepository tentativeQcmRepository;

    public QcmService(
            QcmRepository qcmRepository,
            ResumeCoursRepository resumeCoursRepository,
            GenerateurQcmPort generateurQcm,
            SessionService sessionService,
            TentativeQcmRepository tentativeQcmRepository
    ) {
        this.qcmRepository = qcmRepository;
        this.resumeCoursRepository = resumeCoursRepository;
        this.generateurQcm = generateurQcm;
        this.sessionService = sessionService;
        this.tentativeQcmRepository = tentativeQcmRepository;
    }

    // Genere a la demande uniquement, comme le resume de cours dont il depend.
    // Mis en cache des la premiere generation, jamais regenere ensuite.
    public Qcm obtenirOuGenererQcm(UUID sessionId, UUID utilisateurId) {
        sessionService.verifierAcces(sessionId, utilisateurId);

        Optional<Qcm> existant = qcmRepository.findBySessionId(sessionId);
        if (existant.isPresent()) {
            return existant.get();
        }

        ResumeCours resumeCours = resumeCoursRepository.findBySessionId(sessionId)
                .filter(rc -> rc.getStatut() == StatutResumeCours.REUSSI)
                .orElseThrow(() -> new AucunResumeCoursDisponibleException(sessionId));

        String contenuCours = construireContenuCours(resumeCours);

        try {
            QcmGenere genere = generateurQcm.genererQcm(contenuCours);
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
            return enregistrerSiAbsent(sessionId, questions, resumeCours.getSegmentsSources(), StatutQcm.REUSSI);
        } catch (Exception e) {
            LOG.warn("Echec de la generation du QCM pour la session {}", sessionId, e);
            return enregistrerSiAbsent(sessionId, List.of(), resumeCours.getSegmentsSources(), StatutQcm.ECHEC);
        }
    }

    private String construireContenuCours(ResumeCours resumeCours) {
        String notionsTexte = resumeCours.getNotions().stream()
                .map(notion -> notion.getTerme() + " : " + notion.getDefinition())
                .collect(Collectors.joining("\n"));
        return resumeCours.getSynthese() + "\n\n" + notionsTexte;
    }

    private Qcm enregistrerSiAbsent(
            UUID sessionId, List<QuestionQcm> questions, List<Integer> segmentsSources, StatutQcm statut
    ) {
        Optional<Qcm> existant = qcmRepository.findBySessionId(sessionId);
        if (existant.isPresent()) {
            // Une execution concurrente a deja cree ce QCM.
            return existant.get();
        }
        Qcm qcm = new Qcm(sessionId, questions, segmentsSources, statut);
        return qcmRepository.save(qcm);
    }

    public Qcm obtenirQcm(UUID sessionId) {
        return qcmRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new QcmNotFoundException(sessionId));
    }

    public TentativeQcm soumettreTentative(UUID sessionId, UUID utilisateurId, List<Integer> reponses) {
        Qcm qcm = obtenirQcm(sessionId);
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

    public Optional<TentativeQcm> obtenirMaTentative(UUID sessionId, UUID utilisateurId) {
        Qcm qcm = obtenirQcm(sessionId);
        return tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId);
    }
}
