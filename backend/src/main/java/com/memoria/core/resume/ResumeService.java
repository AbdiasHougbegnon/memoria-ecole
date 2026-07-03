package com.memoria.core.resume;

import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
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

    @Async
    @EventListener
    public void surSessionTerminee(SessionTermineeEvent evenement) {
        UUID sessionId = evenement.sessionId();
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
            enregistrer(sessionId, genere.texteResume(), genere.pointsCles(), segmentsSources, ResumeStatut.REUSSI);
        } catch (Exception e) {
            LOG.warn("Echec de la generation du resume pour la session {}", sessionId, e);
            enregistrer(sessionId, null, List.of(), segmentsSources, ResumeStatut.ECHEC);
        }
    }

    private void enregistrer(
            UUID sessionId,
            String texteResume,
            List<String> pointsCles,
            List<Integer> segmentsSources,
            ResumeStatut statut
    ) {
        Resume resume = new Resume(sessionId, texteResume, pointsCles, segmentsSources, statut);
        resumeRepository.save(resume);
    }

    public Resume obtenirResume(UUID sessionId) {
        return resumeRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResumeNotFoundException(sessionId));
    }
}
