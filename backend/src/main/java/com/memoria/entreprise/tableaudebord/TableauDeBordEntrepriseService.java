package com.memoria.entreprise.tableaudebord;

import com.memoria.entreprise.engagement.Engagement;
import com.memoria.entreprise.engagement.EngagementRepository;
import com.memoria.entreprise.engagement.StatutEngagement;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TableauDeBordEntrepriseService {

    // Fenetre fixe, pas de filtre interactif dans ce premier increment de
    // "suivi fin" -- voir docs/phases/phase-10-suivi-fin-tableau-de-bord.md.
    private static final int SEMAINES_TENDANCE = 8;

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
        double tauxRejet = total == 0 ? 0.0 : (double) rejetes / total;

        Instant maintenant = Instant.now();
        long enRetard = engagements.stream()
                .filter(engagement -> engagement.getStatut() == StatutEngagement.CONFIRME)
                .filter(engagement -> engagement.getDateEcheance() != null && engagement.getDateEcheance().isBefore(maintenant))
                .count();

        return new TableauDeBordEntrepriseResponse(
                total, parStatut, tauxCompletion, enRetard, tauxRejet,
                calculerDelaiMoyenTraitementJours(engagements),
                calculerTendanceHebdomadaire(engagements)
        );
    }

    // dateDerniereMaj n'est modifiee que par confirmer/rejeter/terminer
    // (jamais par planifierEcheance ni les indicateurs de rappel) : comme la
    // machine a etats est lineaire et sans cycle, dateDerniereMaj d'un
    // engagement TERMINE est fiable comme "date d'entree dans cet etat" --
    // le delai total creation -> terminaison est donc calculable sans
    // nouvelle table d'historique. Ce que ca ne permet PAS : le delai de
    // l'etape intermediaire EN_ATTENTE -> CONFIRME une fois l'engagement
    // termine (l'horodatage de la confirmation est ecrase) -- limite
    // assumee, voir docs/phases/phase-10-suivi-fin-tableau-de-bord.md.
    private Double calculerDelaiMoyenTraitementJours(List<Engagement> engagements) {
        List<Engagement> termines = engagements.stream()
                .filter(engagement -> engagement.getStatut() == StatutEngagement.TERMINE)
                .toList();
        if (termines.isEmpty()) {
            return null;
        }
        double totalJours = termines.stream()
                .mapToDouble(engagement -> Duration.between(engagement.getDateCreation(), engagement.getDateDerniereMaj()).toMinutes() / (60.0 * 24.0))
                .sum();
        return totalJours / termines.size();
    }

    private List<PointTendanceHebdomadaire> calculerTendanceHebdomadaire(List<Engagement> engagements) {
        LocalDate debutSemaineCourante = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);

        List<LocalDate> semaines = new ArrayList<>();
        for (int i = SEMAINES_TENDANCE - 1; i >= 0; i--) {
            semaines.add(debutSemaineCourante.minusWeeks(i));
        }

        Map<LocalDate, Long> creesParSemaine = new LinkedHashMap<>();
        Map<LocalDate, Long> termineesParSemaine = new LinkedHashMap<>();
        for (LocalDate semaine : semaines) {
            creesParSemaine.put(semaine, 0L);
            termineesParSemaine.put(semaine, 0L);
        }

        for (Engagement engagement : engagements) {
            LocalDate semaineCreation = debutDeSemaine(engagement.getDateCreation());
            creesParSemaine.computeIfPresent(semaineCreation, (semaine, compte) -> compte + 1);

            if (engagement.getStatut() == StatutEngagement.TERMINE) {
                LocalDate semaineTerminaison = debutDeSemaine(engagement.getDateDerniereMaj());
                termineesParSemaine.computeIfPresent(semaineTerminaison, (semaine, compte) -> compte + 1);
            }
        }

        return semaines.stream()
                .map(semaine -> new PointTendanceHebdomadaire(semaine, creesParSemaine.get(semaine), termineesParSemaine.get(semaine)))
                .toList();
    }

    private LocalDate debutDeSemaine(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().with(DayOfWeek.MONDAY);
    }
}
