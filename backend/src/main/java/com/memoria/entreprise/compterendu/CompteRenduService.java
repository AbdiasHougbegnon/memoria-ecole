package com.memoria.entreprise.compterendu;

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
// vocabulaire Entreprise -- le moteur ne connait ni "decision" ni "action".
@Service
public class CompteRenduService {

    private static final Logger LOG = LoggerFactory.getLogger(CompteRenduService.class);

    private final CompteRenduRepository compteRenduRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final GenerateurCompteRenduPort generateurCompteRendu;
    private final SessionService sessionService;

    public CompteRenduService(
            CompteRenduRepository compteRenduRepository,
            TranscriptionRepository transcriptionRepository,
            GenerateurCompteRenduPort generateurCompteRendu,
            SessionService sessionService
    ) {
        this.compteRenduRepository = compteRenduRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.generateurCompteRendu = generateurCompteRendu;
        this.sessionService = sessionService;
    }

    // Genere a la demande uniquement (comme les resumes COURT/ACTIONS) : un
    // compte rendu complet est un appel Azure OpenAI de plus par session, on
    // ne veut pas l'ajouter automatiquement en plus du resume DETAILLE deja
    // genere a la fin de session (discipline de couts du projet). Mis en
    // cache des la premiere generation, jamais regenere ensuite.
    public CompteRendu obtenirOuGenererCompteRendu(UUID sessionId) {
        sessionService.obtenirSession(sessionId);

        Optional<CompteRendu> existant = compteRenduRepository.findBySessionId(sessionId);
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
            CompteRenduGenere genere = generateurCompteRendu.genererCompteRendu(transcriptComplet);
            List<ActionCompteRendu> actions = genere.actions().stream()
                    .map(action -> new ActionCompteRendu(action.description(), action.responsable(), action.echeance()))
                    .toList();
            return enregistrerSiAbsent(
                    sessionId, genere.synthese(), genere.decisions(), actions, segmentsSources, StatutCompteRendu.REUSSI
            );
        } catch (Exception e) {
            LOG.warn("Echec de la generation du compte rendu pour la session {}", sessionId, e);
            return enregistrerSiAbsent(sessionId, null, List.of(), List.of(), segmentsSources, StatutCompteRendu.ECHEC);
        }
    }

    private List<Transcription> transcriptionsReussies(UUID sessionId) {
        return transcriptionRepository
                .findBySessionIdOrderByNumeroSequenceAsc(sessionId).stream()
                .filter(transcription -> transcription.getStatut() == TranscriptionStatut.REUSSIE)
                .toList();
    }

    private CompteRendu enregistrerSiAbsent(
            UUID sessionId,
            String synthese,
            List<String> decisions,
            List<ActionCompteRendu> actions,
            List<Integer> segmentsSources,
            StatutCompteRendu statut
    ) {
        Optional<CompteRendu> existant = compteRenduRepository.findBySessionId(sessionId);
        if (existant.isPresent()) {
            // Une execution concurrente a deja cree ce compte rendu.
            return existant.get();
        }
        CompteRendu compteRendu = new CompteRendu(sessionId, synthese, decisions, actions, segmentsSources, statut);
        return compteRenduRepository.save(compteRendu);
    }

    public CompteRendu obtenirCompteRendu(UUID sessionId) {
        return compteRenduRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CompteRenduNotFoundException(sessionId));
    }
}
