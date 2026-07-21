package com.memoria.entreprise.tableaudebord;

import com.memoria.entreprise.engagement.Engagement;
import com.memoria.entreprise.engagement.EngagementRepository;
import com.memoria.entreprise.engagement.StatutEngagement;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class TableauDeBordEntrepriseService {

    private final EngagementRepository engagementRepository;

    public TableauDeBordEntrepriseService(EngagementRepository engagementRepository) {
        this.engagementRepository = engagementRepository;
    }

    public TableauDeBordEntrepriseResponse obtenirTableauDeBord() {
        List<Engagement> engagements = engagementRepository.findAll();

        Map<StatutEngagement, Long> parStatut = new EnumMap<>(StatutEngagement.class);
        for (StatutEngagement statut : StatutEngagement.values()) {
            parStatut.put(statut, 0L);
        }
        for (Engagement engagement : engagements) {
            parStatut.merge(engagement.getStatut(), 1L, Long::sum);
        }

        long total = engagements.size();
        long termines = parStatut.get(StatutEngagement.TERMINE);
        long rejetes = parStatut.get(StatutEngagement.REJETE);
        // Un engagement rejete n'etait jamais destine a etre termine : exclu
        // du denominateur pour ne pas fausser le taux de completion a la baisse.
        long denominateurCompletion = total - rejetes;
        double tauxCompletion = denominateurCompletion == 0 ? 0.0 : (double) termines / denominateurCompletion;

        Instant maintenant = Instant.now();
        long enRetard = engagements.stream()
                .filter(engagement -> engagement.getStatut() == StatutEngagement.CONFIRME)
                .filter(engagement -> engagement.getDateEcheance() != null && engagement.getDateEcheance().isBefore(maintenant))
                .count();

        return new TableauDeBordEntrepriseResponse(total, parStatut, tauxCompletion, enRetard);
    }
}
