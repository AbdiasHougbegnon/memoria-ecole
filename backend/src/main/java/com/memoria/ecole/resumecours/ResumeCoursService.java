package com.memoria.ecole.resumecours;

import com.memoria.core.session.SessionService;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// S'appuie sur le moteur (transcription, session) sans y ajouter de
// vocabulaire Ecole -- le moteur ne connait ni "notion" ni "definition".
@Service
public class ResumeCoursService {

    private static final Logger LOG = LoggerFactory.getLogger(ResumeCoursService.class);

    private final ResumeCoursRepository resumeCoursRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final GenerateurResumeCoursPort generateurResumeCours;
    private final SessionService sessionService;

    public ResumeCoursService(
            ResumeCoursRepository resumeCoursRepository,
            TranscriptionRepository transcriptionRepository,
            GenerateurResumeCoursPort generateurResumeCours,
            SessionService sessionService
    ) {
        this.resumeCoursRepository = resumeCoursRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.generateurResumeCours = generateurResumeCours;
        this.sessionService = sessionService;
    }

    // Genere a la demande uniquement (comme le compte rendu complet cote
    // Entreprise) : un appel Azure OpenAI de plus par session, pas ajoute
    // automatiquement en fin de session. Mis en cache des la premiere
    // generation, jamais regenere ensuite.
    public ResumeCours obtenirOuGenererResumeCours(UUID sessionId, UUID utilisateurId) {
        sessionService.verifierAcces(sessionId, utilisateurId);

        Optional<ResumeCours> existant = resumeCoursRepository.findBySessionId(sessionId);
        if (existant.isPresent()) {
            return existant.get();
        }

        List<Transcription> transcriptionsReussies = transcriptionsReussies(sessionId);
        if (transcriptionsReussies.isEmpty()) {
            throw new AucuneTranscriptionDisponibleException(sessionId);
        }

        List<Integer> segmentsSources = transcriptionsReussies.stream()
                .map(Transcription::getNumeroSequence)
                .toList();
        String transcriptComplet = transcriptionsReussies.stream()
                .map(Transcription::getTexte)
                .collect(Collectors.joining(" "));

        try {
            ResumeCoursGenere genere = generateurResumeCours.genererResumeCours(transcriptComplet);
            List<NotionCours> notions = genere.notions().stream()
                    .map(notion -> new NotionCours(notion.terme(), notion.definition()))
                    .toList();
            return enregistrerSiAbsent(
                    sessionId, genere.synthese(), notions, genere.pointsARevoir(), segmentsSources, StatutResumeCours.REUSSI
            );
        } catch (Exception e) {
            LOG.warn("Echec de la generation du resume de cours pour la session {}", sessionId, e);
            return enregistrerSiAbsent(sessionId, null, List.of(), List.of(), segmentsSources, StatutResumeCours.ECHEC);
        }
    }

    private List<Transcription> transcriptionsReussies(UUID sessionId) {
        return transcriptionRepository
                .findBySessionIdOrderByNumeroSequenceAsc(sessionId).stream()
                .filter(transcription -> transcription.getStatut() == TranscriptionStatut.REUSSIE)
                .toList();
    }

    private ResumeCours enregistrerSiAbsent(
            UUID sessionId,
            String synthese,
            List<NotionCours> notions,
            List<String> pointsARevoir,
            List<Integer> segmentsSources,
            StatutResumeCours statut
    ) {
        Optional<ResumeCours> existant = resumeCoursRepository.findBySessionId(sessionId);
        if (existant.isPresent()) {
            // Une execution concurrente a deja cree ce resume de cours.
            return existant.get();
        }
        ResumeCours resumeCours = new ResumeCours(sessionId, synthese, notions, pointsARevoir, segmentsSources, statut);
        return resumeCoursRepository.save(resumeCours);
    }

    public ResumeCours obtenirResumeCours(UUID sessionId) {
        return resumeCoursRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResumeCoursNotFoundException(sessionId));
    }

    // Fiche telechargeable (phase 22b) : texte brut, pas de PDF -- aucune
    // dependance de generation PDF dans le projet, proportionne pour ce lot.
    // Contrairement a obtenirResumeCours (lecture simple, non controlee
    // aujourd'hui, voir l'audit), le telechargement verifie l'acces : c'est
    // une action explicite de l'utilisateur, pas un simple GET d'affichage.
    public String genererFichierTexte(UUID sessionId, UUID utilisateurId) {
        sessionService.verifierAcces(sessionId, utilisateurId);
        ResumeCours resumeCours = resumeCoursRepository.findBySessionId(sessionId)
                .filter(rc -> rc.getStatut() == StatutResumeCours.REUSSI)
                .orElseThrow(() -> new ResumeCoursNotFoundException(sessionId));

        StringBuilder texte = new StringBuilder();
        texte.append("Resume de cours\n================\n\n");
        texte.append(resumeCours.getSynthese()).append("\n");

        if (!resumeCours.getNotions().isEmpty()) {
            texte.append("\nNotions\n-------\n");
            for (NotionCours notion : resumeCours.getNotions()) {
                texte.append("- ").append(notion.getTerme()).append(" : ").append(notion.getDefinition()).append("\n");
            }
        }

        if (!resumeCours.getPointsARevoir().isEmpty()) {
            texte.append("\nPoints a revoir\n---------------\n");
            for (String point : resumeCours.getPointsARevoir()) {
                texte.append("- ").append(point).append("\n");
            }
        }

        return texte.toString();
    }
}
