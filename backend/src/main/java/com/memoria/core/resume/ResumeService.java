package com.memoria.core.resume;

import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import com.memoria.core.transcription.ToutesTranscriptionsTermineesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    private static final Logger LOG = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumeRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final GenerateurResumePort generateurResume;

    public ResumeService(
            ResumeRepository resumeRepository,
            TranscriptionRepository transcriptionRepository,
            GenerateurResumePort generateurResume
    ) {
        this.resumeRepository = resumeRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.generateurResume = generateurResume;
    }

    // La fin de session et l'arrivee de la derniere transcription peuvent
    // survenir dans n'importe quel ordre (course connue) : on tente de
    // generer le resume dans les deux cas, avec une garde d'idempotence.
    @Async
    @EventListener
    public void surSessionTerminee(SessionTermineeEvent evenement) {
        genererResumeSiPossible(evenement.sessionId());
    }

    @Async
    @EventListener
    public void surToutesTranscriptionsTerminees(ToutesTranscriptionsTermineesEvent evenement) {
        genererResumeSiPossible(evenement.sessionId());
    }

    private void genererResumeSiPossible(UUID sessionId) {
        if (resumeRepository.findBySessionId(sessionId).isPresent()) {
            return;
        }

        List<Transcription> transcriptionsReussies = transcriptionRepository
                .findBySessionIdOrderByNumeroSequenceAsc(sessionId).stream()
                .filter(transcription -> transcription.getStatut() == TranscriptionStatut.REUSSIE)
                .toList();

        if (transcriptionsReussies.isEmpty()) {
            return;
        }

        List<Integer> segmentsSources = transcriptionsReussies.stream()
                .map(Transcription::getNumeroSequence)
                .toList();
        String transcriptComplet = transcriptionsReussies.stream()
                .map(Transcription::getTexte)
                .collect(Collectors.joining(" "));

        try {
            ResumeGenere genere = generateurResume.genererResume(transcriptComplet);
            enregistrerSiAbsent(sessionId, genere.texteResume(), genere.pointsCles(), segmentsSources, ResumeStatut.REUSSI);
        } catch (Exception e) {
            LOG.warn("Echec de la generation du resume pour la session {}", sessionId, e);
            enregistrerSiAbsent(sessionId, null, List.of(), segmentsSources, ResumeStatut.ECHEC);
        }
    }

    private void enregistrerSiAbsent(
            UUID sessionId,
            String texteResume,
            List<String> pointsCles,
            List<Integer> segmentsSources,
            ResumeStatut statut
    ) {
        if (resumeRepository.findBySessionId(sessionId).isPresent()) {
            // Une execution concurrente (course session-terminee /
            // toutes-transcriptions-terminees) a deja cree le resume.
            return;
        }
        Resume resume = new Resume(sessionId, texteResume, pointsCles, segmentsSources, statut);
        resumeRepository.save(resume);
    }

    public Resume obtenirResume(UUID sessionId) {
        return resumeRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResumeNotFoundException(sessionId));
    }
}
