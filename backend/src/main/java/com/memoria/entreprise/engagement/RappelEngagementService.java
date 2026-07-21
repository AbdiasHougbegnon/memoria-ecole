package com.memoria.entreprise.engagement;

import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.email.EnvoyeurEmail;
import com.memoria.core.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// "Rappels contextualises adresses d'abord a la personne concernee" (master
// prompt) -- cible desormais precisement engagement.responsableUtilisateurId
// quand la reconnaissance de voix recurrente a resolu qui est concerne (voir
// ConstructeurTranscriptLabelise). A defaut (locuteur non identifie), le
// rappel part aux participants de la session (createur + membres du couloir
// si rattachee) -- comportement de repli historique, toujours utile pour les
// engagements crees avant l'identification ou dont le locuteur n'a jamais
// ete identifie.
@Service
public class RappelEngagementService {

    private static final Logger LOG = LoggerFactory.getLogger(RappelEngagementService.class);
    private static final Duration FENETRE_ECHEANCE_PROCHE = Duration.ofHours(24);

    private final EngagementRepository engagementRepository;
    private final SessionService sessionService;
    private final UtilisateurRepository utilisateurRepository;
    private final EnvoyeurEmail envoyeurEmail;

    public RappelEngagementService(
            EngagementRepository engagementRepository,
            SessionService sessionService,
            UtilisateurRepository utilisateurRepository,
            EnvoyeurEmail envoyeurEmail) {
        this.engagementRepository = engagementRepository;
        this.sessionService = sessionService;
        this.utilisateurRepository = utilisateurRepository;
        this.envoyeurEmail = envoyeurEmail;
    }

    // Toutes les heures par defaut : largement suffisant pour une fenetre de
    // 24h, evite de solliciter le serveur SMTP en continu pour rien.
    // Configurable (memoria.rappel.cron) pour permettre une verification en
    // conditions reelles sans attendre une heure pleine.
    @Scheduled(cron = "${memoria.rappel.cron}")
    public void verifierEcheances() {
        Instant maintenant = Instant.now();
        List<Engagement> engagementsASurveiller =
                engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME);

        for (Engagement engagement : engagementsASurveiller) {
            try {
                traiterEngagement(engagement, maintenant);
            } catch (Exception e) {
                LOG.warn("Echec du traitement du rappel pour l'engagement {}", engagement.getId(), e);
            }
        }
    }

    private void traiterEngagement(Engagement engagement, Instant maintenant) {
        Instant echeance = engagement.getDateEcheance();

        if (!engagement.isRappelRetardEnvoye() && echeance.isBefore(maintenant)) {
            if (envoyerRappel(engagement, "Cet engagement est en retard.")) {
                engagement.marquerRappelRetardEnvoye();
                engagementRepository.save(engagement);
            }
        } else if (!engagement.isRappelEcheanceProcheEnvoye()
                && !echeance.isBefore(maintenant)
                && echeance.isBefore(maintenant.plus(FENETRE_ECHEANCE_PROCHE))) {
            if (envoyerRappel(engagement, "L'echeance de cet engagement approche.")) {
                engagement.marquerRappelEcheanceProcheEnvoye();
                engagementRepository.save(engagement);
            }
        }
    }

    // Renvoie false si aucun destinataire n'a pu etre resolu -- dans ce cas
    // l'appelant ne doit pas marquer le rappel comme envoye, pour qu'il
    // puisse encore se declencher plus tard (ex: un membre rejoint le
    // couloir de la session entre-temps).
    private boolean envoyerRappel(Engagement engagement, String messageContexte) {
        List<String> destinataires = resoudreDestinataires(engagement);
        if (destinataires.isEmpty()) {
            LOG.info("Aucun destinataire resolu pour l'engagement {}, rappel non envoye", engagement.getId());
            return false;
        }

        String sujet = "Rappel Memoria : " + engagement.getDescription();
        String corps = messageContexte + "\n\n"
                + "Description : " + engagement.getDescription() + "\n"
                + (engagement.getResponsable() != null ? "Responsable : " + engagement.getResponsable() + "\n" : "")
                + "Echeance : " + engagement.getDateEcheance();

        for (String destinataire : destinataires) {
            envoyeurEmail.envoyer(destinataire, sujet, corps);
        }
        return true;
    }

    // Cible precisement le responsable identifie quand on le connait ; sinon
    // replie sur tous les participants de la session (comportement historique).
    private List<String> resoudreDestinataires(Engagement engagement) {
        if (engagement.getResponsableUtilisateurId() != null) {
            return utilisateurRepository.findById(engagement.getResponsableUtilisateurId())
                    .map(Utilisateur::getEmail)
                    .map(List::of)
                    .orElseGet(() -> sessionService.resoudreEmailsParticipants(engagement.getSessionId()));
        }
        return sessionService.resoudreEmailsParticipants(engagement.getSessionId());
    }
}
