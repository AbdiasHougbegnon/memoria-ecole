package com.memoria.entreprise.engagement;

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

import java.util.List;
import java.util.UUID;

@Service
public class EngagementService {

    private static final Logger LOG = LoggerFactory.getLogger(EngagementService.class);

    private final EngagementRepository engagementRepository;
    private final CompteRenduRepository compteRenduRepository;

    public EngagementService(EngagementRepository engagementRepository, CompteRenduRepository compteRenduRepository) {
        this.engagementRepository = engagementRepository;
        this.compteRenduRepository = compteRenduRepository;
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
        return engagementRepository.save(engagement);
    }

    private Engagement obtenirEngagement(UUID id) {
        return engagementRepository.findById(id).orElseThrow(() -> new EngagementNotFoundException(id));
    }
}
