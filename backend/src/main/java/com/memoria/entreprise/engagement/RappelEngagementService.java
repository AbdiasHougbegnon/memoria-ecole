package com.memoria.entreprise.engagement;

import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.couloir.MembreCouloirRepository;
import com.memoria.core.email.EnvoyeurEmail;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// "Rappels contextualises adresses d'abord a la personne concernee" (master
// prompt) -- ici, faute de lien fiable entre le champ texte libre
// Engagement.responsable (un label de diarization type "Intervenant 2") et
// un compte utilisateur reel, le rappel part aux participants de la session
// (createur + membres du couloir si rattachee). Voir la doc de cette brique
// pour la decision et ses limites.
@Service
public class RappelEngagementService {

    private static final Logger LOG = LoggerFactory.getLogger(RappelEngagementService.class);
    private static final Duration FENETRE_ECHEANCE_PROCHE = Duration.ofHours(24);

    private final EngagementRepository engagementRepository;
    private final SessionRepository sessionRepository;
    private final MembreCouloirRepository membreCouloirRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EnvoyeurEmail envoyeurEmail;

    public RappelEngagementService(
            EngagementRepository engagementRepository,
            SessionRepository sessionRepository,
            MembreCouloirRepository membreCouloirRepository,
            UtilisateurRepository utilisateurRepository,
            EnvoyeurEmail envoyeurEmail) {
        this.engagementRepository = engagementRepository;
        this.sessionRepository = sessionRepository;
        this.membreCouloirRepository = membreCouloirRepository;
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
        List<String> destinataires = resoudreDestinataires(engagement.getSessionId());
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

    private List<String> resoudreDestinataires(UUID sessionId) {
        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return List.of();
        }

        Set<UUID> utilisateurIds = new HashSet<>();
        if (session.get().getCreateurId() != null) {
            utilisateurIds.add(session.get().getCreateurId());
        }
        if (session.get().getCouloirId() != null) {
            membreCouloirRepository.findByCouloirId(session.get().getCouloirId())
                    .forEach(membre -> utilisateurIds.add(membre.getUtilisateurId()));
        }

        return utilisateurIds.stream()
                .map(utilisateurRepository::findById)
                .flatMap(Optional::stream)
                .map(Utilisateur::getEmail)
                .toList();
    }
}
