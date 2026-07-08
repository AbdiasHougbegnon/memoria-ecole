package com.memoria.core.filmemoire;

import com.memoria.core.recherche.GenerateurEmbeddingPort;
import com.memoria.core.resume.AucuneTranscriptionDisponibleException;
import com.memoria.core.resume.Resume;
import com.memoria.core.resume.ResumeService;
import com.memoria.core.resume.ResumeStatut;
import com.memoria.core.resume.ResumeType;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.ToutesTranscriptionsTermineesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class FilMemoireService {

    private static final Logger LOG = LoggerFactory.getLogger(FilMemoireService.class);
    private static final int NOMBRE_CANDIDATS_MAX = 3;

    private final FilMemoireRepository filMemoireRepository;
    private final ResumeService resumeService;
    private final GenerateurEmbeddingPort generateurEmbedding;
    private final GenerateurFilMemoirePort generateurFilMemoire;

    public FilMemoireService(
            FilMemoireRepository filMemoireRepository,
            ResumeService resumeService,
            GenerateurEmbeddingPort generateurEmbedding,
            GenerateurFilMemoirePort generateurFilMemoire
    ) {
        this.filMemoireRepository = filMemoireRepository;
        this.resumeService = resumeService;
        this.generateurEmbedding = generateurEmbedding;
        this.generateurFilMemoire = generateurFilMemoire;
    }

    // Meme course connue que pour le resume et la recherche semantique (fin
    // de session / derniere transcription dans un ordre imprevisible) : les
    // deux declencheurs appellent la meme logique, avec une garde
    // d'idempotence a l'entree et revalidee juste avant chaque sauvegarde.
    @Async
    @EventListener
    public void surSessionTerminee(SessionTermineeEvent evenement) {
        traiterSiPossible(evenement.sessionId());
    }

    @Async
    @EventListener
    public void surToutesTranscriptionsTerminees(ToutesTranscriptionsTermineesEvent evenement) {
        traiterSiPossible(evenement.sessionId());
    }

    private void traiterSiPossible(UUID sessionId) {
        if (filMemoireRepository.existsBySessionId(sessionId)) {
            return;
        }

        Resume resume;
        try {
            // Reutilise le resume DETAILLE deja genere automatiquement en fin
            // de session (ou le genere s'il manque encore) plutot que de
            // relire la transcription : evite un deuxieme prompt de
            // synthese redondant, et garantit qu'on ne s'execute qu'une fois
            // le resume reellement disponible (pas de dependance d'ordre
            // fragile avec ResumeService).
            resume = resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE);
        } catch (AucuneTranscriptionDisponibleException e) {
            return;
        }
        if (resume.getStatut() != ResumeStatut.REUSSI || resume.getTexteResume() == null) {
            return;
        }

        try {
            regrouper(sessionId, resume.getTexteResume());
        } catch (Exception e) {
            LOG.warn("Echec du regroupement en fil de memoire pour la session {}", sessionId, e);
        }
    }

    private void regrouper(UUID sessionId, String resumeSession) {
        float[] vecteurSession = generateurEmbedding.genererEmbeddings(List.of(resumeSession)).get(0);

        List<FilMemoire> candidatsTries = filMemoireRepository.findAll().stream()
                .sorted(Comparator.comparingDouble(
                        (FilMemoire f) -> VecteurUtils.similariteCosinus(vecteurSession, VecteurUtils.depuisOctets(f.getEmbedding()))
                ).reversed())
                .limit(NOMBRE_CANDIDATS_MAX)
                .toList();

        List<CandidatFilMemoire> candidats = candidatsTries.stream()
                .map(f -> new CandidatFilMemoire(f.getId(), f.getNom(), f.getResumeCumulatif()))
                .toList();

        DecisionFilMemoire decision = generateurFilMemoire.deciderFil(resumeSession, candidats);

        if (filMemoireRepository.existsBySessionId(sessionId)) {
            // Une execution concurrente a deja traite cette session.
            return;
        }

        if (decision.filExistantId() != null) {
            rejoindreFilExistant(sessionId, decision, candidatsTries);
        } else {
            creerNouveauFil(sessionId, decision, resumeSession);
        }
    }

    private void rejoindreFilExistant(UUID sessionId, DecisionFilMemoire decision, List<FilMemoire> candidatsTries) {
        FilMemoire fil = candidatsTries.stream()
                .filter(f -> f.getId().equals(decision.filExistantId()))
                .findFirst()
                .orElseThrow(() -> new GenerationFilMemoireException(
                        "Le fil designe par la decision (" + decision.filExistantId() + ") ne fait pas partie des candidats"
                ));

        String resumeCumulatif = decision.resumeMisAJour() != null ? decision.resumeMisAJour() : fil.getResumeCumulatif();
        float[] vecteurMisAJour = generateurEmbedding.genererEmbeddings(List.of(resumeCumulatif)).get(0);
        fil.mettreAJour(resumeCumulatif, VecteurUtils.versOctets(vecteurMisAJour), sessionId);
        filMemoireRepository.save(fil);
    }

    private void creerNouveauFil(UUID sessionId, DecisionFilMemoire decision, String resumeSession) {
        String resumeInitial = decision.resumeMisAJour() != null ? decision.resumeMisAJour() : resumeSession;
        String nom = decision.nouveauNom() != null ? decision.nouveauNom() : "Sans titre";
        float[] vecteurFil = generateurEmbedding.genererEmbeddings(List.of(resumeInitial)).get(0);
        FilMemoire nouveauFil = new FilMemoire(
                nom, resumeInitial, VecteurUtils.versOctets(vecteurFil), new ArrayList<>(List.of(sessionId))
        );
        filMemoireRepository.save(nouveauFil);
    }

    public List<FilMemoire> listerFils() {
        return filMemoireRepository.findAllByOrderByDateMiseAJourDesc();
    }
}
