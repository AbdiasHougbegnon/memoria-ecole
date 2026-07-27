package com.memoria.entreprise.compterendu;

import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.session.SessionService;
import com.memoria.core.transcription.ConstructeurTranscriptLabelise;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// S'appuie sur le moteur (transcription, session) sans y ajouter de
// vocabulaire Entreprise -- le moteur ne connait ni "decision" ni "action".
@Service
public class CompteRenduService {

    private static final Logger LOG = LoggerFactory.getLogger(CompteRenduService.class);

    private final CompteRenduRepository compteRenduRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final GenerateurCompteRenduPort generateurCompteRendu;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final UtilisateurRepository utilisateurRepository;

    public CompteRenduService(
            CompteRenduRepository compteRenduRepository,
            TranscriptionRepository transcriptionRepository,
            GenerateurCompteRenduPort generateurCompteRendu,
            SessionService sessionService,
            ApplicationEventPublisher eventPublisher,
            UtilisateurRepository utilisateurRepository
    ) {
        this.compteRenduRepository = compteRenduRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.generateurCompteRendu = generateurCompteRendu;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
        this.utilisateurRepository = utilisateurRepository;
    }

    // Genere a la demande uniquement (comme les resumes COURT/ACTIONS) : un
    // compte rendu complet est un appel Azure OpenAI de plus par session, on
    // ne veut pas l'ajouter automatiquement en plus du resume DETAILLE deja
    // genere a la fin de session (discipline de couts du projet). Mis en
    // cache des la premiere generation, jamais regenere ensuite.
    public CompteRendu obtenirOuGenererCompteRendu(UUID sessionId, UUID utilisateurId) {
        sessionService.verifierAcces(sessionId, utilisateurId);

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
        // Etiquette chaque segment par locuteur (vrai nom si identifie, sinon
        // "Intervenant X") avant de soumettre le texte a l'IA -- sans ca,
        // celle-ci ne recoit qu'un texte concatene sans aucun repere de
        // locuteur et ne peut pas suivre la consigne "reprends ces reperes
        // tels quels" (voir ConstructeurTranscriptLabelise).
        ConstructeurTranscriptLabelise.Resultat transcriptLabelise = ConstructeurTranscriptLabelise.construire(
                transcriptionsReussies, this::nomUtilisateur);

        try {
            CompteRenduGenere genere = generateurCompteRendu.genererCompteRendu(transcriptLabelise.texte());
            List<ActionCompteRendu> actions = genere.actions().stream()
                    .map(action -> new ActionCompteRendu(
                            action.description(),
                            action.responsable(),
                            action.echeance(),
                            action.responsable() == null ? null : transcriptLabelise.utilisateurIdParLabel().get(action.responsable())))
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

    private String nomUtilisateur(UUID utilisateurId) {
        return utilisateurRepository.findById(utilisateurId).map(Utilisateur::nomAffichage).orElse(null);
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
        CompteRendu sauvegarde = compteRenduRepository.save(compteRendu);
        if (statut == StatutCompteRendu.REUSSI) {
            // Le suivi des engagements (Entreprise) transforme les actions
            // extraites en elements traces et confirmables par un humain :
            // voir EngagementService, qui ecoute cet evenement.
            eventPublisher.publishEvent(new CompteRenduGenereEvent(sessionId));
        }
        return sauvegarde;
    }

    public CompteRendu obtenirCompteRendu(UUID sessionId) {
        return compteRenduRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CompteRenduNotFoundException(sessionId));
    }
}
