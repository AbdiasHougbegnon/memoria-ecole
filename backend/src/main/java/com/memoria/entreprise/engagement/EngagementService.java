package com.memoria.entreprise.engagement;

import com.memoria.core.email.EnvoyeurEmail;
import com.memoria.core.session.SessionService;
import com.memoria.entreprise.compterendu.ActionCompteRendu;
import com.memoria.entreprise.compterendu.CompteRendu;
import com.memoria.entreprise.compterendu.CompteRenduGenereEvent;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.compterendu.StatutCompteRendu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EngagementService {

    private static final Logger LOG = LoggerFactory.getLogger(EngagementService.class);

    private final EngagementRepository engagementRepository;
    private final CompteRenduRepository compteRenduRepository;
    private final SessionService sessionService;
    private final EnvoyeurEmail envoyeurEmail;

    public EngagementService(
            EngagementRepository engagementRepository,
            CompteRenduRepository compteRenduRepository,
            SessionService sessionService,
            EnvoyeurEmail envoyeurEmail) {
        this.engagementRepository = engagementRepository;
        this.compteRenduRepository = compteRenduRepository;
        this.sessionService = sessionService;
        this.envoyeurEmail = envoyeurEmail;
    }

    // Un compte rendu complet est genere a la demande (pas automatiquement a
    // la fin de session, cf. CompteRenduService) : cet ecouteur ne se
    // declenche donc qu'une fois par session, quand l'utilisateur demande
    // effectivement le compte rendu.
    @Async
    @EventListener
    public void surCompteRenduGenere(CompteRenduGenereEvent evenement) {
        UUID sessionId = evenement.sessionId();
        if (engagementRepository.existsBySessionId(sessionId)) {
            return;
        }

        CompteRendu compteRendu = compteRenduRepository.findBySessionId(sessionId).orElse(null);
        if (compteRendu == null || compteRendu.getStatut() != StatutCompteRendu.REUSSI) {
            return;
        }

        try {
            creerEngagements(sessionId, compteRendu.getActions());
        } catch (Exception e) {
            LOG.warn("Echec de la creation des engagements pour la session {}", sessionId, e);
        }
    }

    private void creerEngagements(UUID sessionId, List<ActionCompteRendu> actions) {
        if (engagementRepository.existsBySessionId(sessionId)) {
            // Une execution concurrente a deja traite cette session.
            return;
        }

        List<Engagement> engagements = actions.stream()
                .filter(action -> action.getDescription() != null && !action.getDescription().isBlank())
                .map(action -> new Engagement(sessionId, action.getDescription(), action.getResponsable(), action.getEcheance()))
                .toList();

        if (!engagements.isEmpty()) {
            engagementRepository.saveAll(engagements);
        }
    }

    public List<Engagement> listerTous() {
        return engagementRepository.findAllByOrderByDateCreationDesc();
    }

    public List<Engagement> listerParStatut(StatutEngagement statut) {
        return engagementRepository.findByStatutOrderByDateCreationDesc(statut);
    }

    public List<Engagement> listerParSession(UUID sessionId) {
        return engagementRepository.findBySessionIdOrderByDateCreationAsc(sessionId);
    }

    public Engagement confirmer(UUID id) {
        Engagement engagement = obtenirEngagement(id);
        engagement.confirmer();
        return engagementRepository.save(engagement);
    }

    public Engagement rejeter(UUID id) {
        Engagement engagement = obtenirEngagement(id);
        engagement.rejeter();
        return engagementRepository.save(engagement);
    }

    public Engagement terminer(UUID id) {
        Engagement engagement = obtenirEngagement(id);
        engagement.terminer();
        Engagement engagementTermine = engagementRepository.save(engagement);
        notifierCompletion(engagementTermine);
        return engagementTermine;
    }

    // "Boucle de responsabilite fermee automatiquement" (master prompt) :
    // notifie les participants de la session qu'un engagement est termine.
    // Ne doit jamais faire echouer terminer() lui-meme -- un email non
    // envoye ne doit pas empecher de marquer un engagement comme fait.
    private void notifierCompletion(Engagement engagement) {
        try {
            List<String> destinataires = sessionService.resoudreEmailsParticipants(engagement.getSessionId());
            if (destinataires.isEmpty()) {
                return;
            }
            String sujet = "Engagement termine : " + engagement.getDescription();
            String corps = "Cet engagement a ete marque comme termine.\n\n"
                    + "Description : " + engagement.getDescription() + "\n"
                    + (engagement.getResponsable() != null ? "Responsable : " + engagement.getResponsable() : "");
            for (String destinataire : destinataires) {
                envoyeurEmail.envoyer(destinataire, sujet, corps);
            }
        } catch (Exception e) {
            LOG.warn("Echec de la notification de completion pour l'engagement {}", engagement.getId(), e);
        }
    }

    public Engagement planifierEcheance(UUID id, Instant dateEcheance) {
        Engagement engagement = obtenirEngagement(id);
        engagement.planifierEcheance(dateEcheance);
        return engagementRepository.save(engagement);
    }

    private Engagement obtenirEngagement(UUID id) {
        return engagementRepository.findById(id).orElseThrow(() -> new EngagementNotFoundException(id));
    }
}
