package com.memoria.core.gouvernance;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

// Duree de conservation parametrable, "instance = tenant" (voir
// docs/gouvernance-donnees.md et le meme raisonnement applique au budget
// Azure en phase-11) : une seule duree globale pour l'instance, pas une
// notion de tenant a construire. Purge planifiee, sans distinction
// personnelle/partagee (contrairement a l'effacement sur demande d'un seul
// utilisateur) -- une politique de retention s'applique a toutes les
// donnees de l'instance.
@Service
public class RetentionService {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);

    private final SessionRepository sessionRepository;
    private final SessionPurgeService sessionPurgeService;
    private final JournalRgpdRepository journalRgpdRepository;
    private final long retentionJours;

    public RetentionService(
            SessionRepository sessionRepository,
            SessionPurgeService sessionPurgeService,
            JournalRgpdRepository journalRgpdRepository,
            @Value("${memoria.rgpd.retention-jours:-1}") long retentionJours
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionPurgeService = sessionPurgeService;
        this.journalRgpdRepository = journalRgpdRepository;
        this.retentionJours = retentionJours;
    }

    // Desactive par defaut (retentionJours < 0) : aucune suppression tant que
    // le client n'a pas configure explicitement sa duree de conservation --
    // meme doctrine "sur par defaut" que memoria.cout.azure.mode-strict en
    // phase-11.
    @Scheduled(cron = "${memoria.rgpd.retention.cron:0 0 3 * * *}")
    public void purgerSessionsExpirees() {
        if (retentionJours < 0) {
            return;
        }

        Instant seuil = Instant.now().minusSeconds(retentionJours * 86_400L);
        List<Session> sessionsExpirees = sessionRepository.findByDateCreationBefore(seuil);
        int nombrePurgees = 0;
        for (Session session : sessionsExpirees) {
            try {
                sessionPurgeService.purgerSessionCompletement(session.getId());
                sessionPurgeService.nettoyerDependancesExternes(session.getId());
                nombrePurgees++;
            } catch (Exception e) {
                LOG.warn("Echec de la purge de retention pour la session {}", session.getId(), e);
            }
        }

        if (nombrePurgees > 0) {
            journalRgpdRepository.save(new JournalRgpd(
                    TypeActionRgpd.PURGE_RETENTION, null,
                    nombrePurgees + " session(s) purgee(s) (retention de " + retentionJours + " jours)"
            ));
        }
    }
}
