package com.memoria.core.resume;

import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.ToutesTranscriptionsTermineesEvent;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    private static final Logger LOG = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumeRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final GenerateurResumePort generateurResume;
    private final SessionService sessionService;

    public ResumeService(
            ResumeRepository resumeRepository,
            TranscriptionRepository transcriptionRepository,
            GenerateurResumePort generateurResume,
            SessionService sessionService
    ) {
        this.resumeRepository = resumeRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.generateurResume = generateurResume;
        this.sessionService = sessionService;
    }

    // La fin de session et l'arrivee de la derniere transcription peuvent
    // survenir dans n'importe quel ordre (course connue) : on tente de
    // generer le resume DETAILLE dans les deux cas, avec une garde
    // d'idempotence. C'est le seul type genere automatiquement ; les autres
    // types (COURT, ACTIONS) sont generes a la demande, voir
    // obtenirOuGenererResume, pour ne pas multiplier les appels Azure OpenAI
    // sans qu'ils soient consultes (discipline de couts du projet).
    @Async
    @EventListener
    public void surSessionTerminee(SessionTermineeEvent evenement) {
        genererResumeDetailleSiPossible(evenement.sessionId());
    }

    @Async
    @EventListener
    public void surToutesTranscriptionsTerminees(ToutesTranscriptionsTermineesEvent evenement) {
        genererResumeDetailleSiPossible(evenement.sessionId());
    }

    private void genererResumeDetailleSiPossible(UUID sessionId) {
        if (resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE).isPresent()) {
            return;
        }

        List<Transcription> transcriptionsReussies = transcriptionsReussies(sessionId);
        if (transcriptionsReussies.isEmpty()) {
            return;
        }

        genererEtEnregistrerSiAbsent(sessionId, ResumeType.DETAILLE, transcriptionsReussies);
    }

    // Generation a la demande d'un type de resume (court, detaille, actions).
    // Mis en cache des la premiere generation reussie ou echouee : jamais
    // regenere ensuite, l'utilisateur revoit toujours le meme resultat.
    public Resume obtenirOuGenererResume(UUID sessionId, ResumeType type) {
        sessionService.obtenirSession(sessionId);

        Optional<Resume> existant = resumeRepository.findBySessionIdAndType(sessionId, type);
        if (existant.isPresent()) {
            return existant.get();
        }

        List<Transcription> transcriptionsReussies = transcriptionsReussies(sessionId);
        if (transcriptionsReussies.isEmpty()) {
            throw new AucuneTranscriptionDisponibleException(sessionId);
        }

        return genererEtEnregistrerSiAbsent(sessionId, type, transcriptionsReussies);
    }

    private List<Transcription> transcriptionsReussies(UUID sessionId) {
        return transcriptionRepository
                .findBySessionIdOrderByNumeroSequenceAsc(sessionId).stream()
                .filter(transcription -> transcription.getStatut() == TranscriptionStatut.REUSSIE)
                .toList();
    }

    private Resume genererEtEnregistrerSiAbsent(
            UUID sessionId,
            ResumeType type,
            List<Transcription> transcriptionsReussies
    ) {
        List<Integer> segmentsSources = transcriptionsReussies.stream()
                .map(Transcription::getNumeroSequence)
                .toList();
        String transcriptComplet = transcriptionsReussies.stream()
                .map(Transcription::getTexte)
                .collect(Collectors.joining(" "));

        try {
            ResumeGenere genere = generateurResume.genererResume(type, transcriptComplet);
            return enregistrerSiAbsent(sessionId, type, genere.texteResume(), genere.pointsCles(), segmentsSources, ResumeStatut.REUSSI);
        } catch (Exception e) {
            LOG.warn("Echec de la generation du resume {} pour la session {}", type, sessionId, e);
            return enregistrerSiAbsent(sessionId, type, null, List.of(), segmentsSources, ResumeStatut.ECHEC);
        }
    }

    private Resume enregistrerSiAbsent(
            UUID sessionId,
            ResumeType type,
            String texteResume,
            List<String> pointsCles,
            List<Integer> segmentsSources,
            ResumeStatut statut
    ) {
        Optional<Resume> existant = resumeRepository.findBySessionIdAndType(sessionId, type);
        if (existant.isPresent()) {
            // Une execution concurrente a deja cree ce resume.
            return existant.get();
        }
        Resume resume = new Resume(sessionId, type, texteResume, pointsCles, segmentsSources, statut);
        try {
            return resumeRepository.save(resume);
        } catch (DataIntegrityViolationException e) {
            // Fenetre de course non couverte par la verification ci-dessus :
            // une execution concurrente a sauvegarde entre notre lecture et
            // notre ecriture (contrainte unique session_id+type). Le
            // "perdant" ne doit pas planter -- il renvoie le resultat du
            // "gagnant" au lieu de laisser l'exception se propager (elle
            // remontait jusqu'aux appelants de obtenirOuGenererResume, par
            // exemple FilMemoireService, en abandonnant silencieusement leur
            // propre traitement).
            return resumeRepository.findBySessionIdAndType(sessionId, type).orElseThrow(() -> e);
        }
    }

    public Resume obtenirResume(UUID sessionId, ResumeType type) {
        return resumeRepository.findBySessionIdAndType(sessionId, type)
                .orElseThrow(() -> new ResumeNotFoundException(sessionId));
    }
}
