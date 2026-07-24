package com.memoria.entreprise.tableaudebord;

import com.memoria.entreprise.engagement.Engagement;
import com.memoria.entreprise.engagement.EngagementRepository;
import com.memoria.entreprise.engagement.StatutEngagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableauDeBordEntrepriseServiceTest {

    @Mock
    private EngagementRepository engagementRepository;

    private TableauDeBordEntrepriseService tableauDeBordEntrepriseService;

    @BeforeEach
    void setUp() {
        tableauDeBordEntrepriseService = new TableauDeBordEntrepriseService(engagementRepository);
    }

    private Engagement engagement(StatutEngagement statut) {
        Engagement engagement = new Engagement(UUID.randomUUID(), "Description", null, null);
        if (statut == StatutEngagement.CONFIRME || statut == StatutEngagement.TERMINE) {
            engagement.confirmer();
        }
        if (statut == StatutEngagement.REJETE) {
            engagement.rejeter();
        }
        if (statut == StatutEngagement.TERMINE) {
            engagement.terminer();
        }
        return engagement;
    }

    // Engagement.dateCreation/dateDerniereMaj sont fixees a Instant.now() par
    // le constructeur/les transitions, sans point d'injection -- un mock
    // Mockito (classe concrete sans methode finale) permet de controler ces
    // horodatages precisement pour tester les agregations temporelles, sans
    // toucher a l'entite de domaine juste pour les besoins des tests.
    private Engagement engagementAvecDates(StatutEngagement statut, Instant dateCreation, Instant dateDerniereMaj) {
        Engagement engagement = mock(Engagement.class);
        when(engagement.getStatut()).thenReturn(statut);
        when(engagement.getDateCreation()).thenReturn(dateCreation);
        // lenient : le code de prod n'appelle getDateDerniereMaj() que pour
        // les engagements TERMINE, ce stub est donc inutilise (et signale
        // comme tel par Mockito en mode strict) pour les autres statuts.
        lenient().when(engagement.getDateDerniereMaj()).thenReturn(dateDerniereMaj);
        return engagement;
    }

    @Test
    void obtenirTableauDeBord_renvoie_zero_partout_si_aucun_engagement() {
        when(engagementRepository.findAll()).thenReturn(List.of());

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.total()).isZero();
        assertThat(resultat.tauxCompletion()).isZero();
        assertThat(resultat.enRetard()).isZero();
        assertThat(resultat.parStatut().get(StatutEngagement.TERMINE)).isZero();
    }

    @Test
    void obtenirTableauDeBord_compte_chaque_statut() {
        when(engagementRepository.findAll()).thenReturn(List.of(
                engagement(StatutEngagement.EN_ATTENTE),
                engagement(StatutEngagement.CONFIRME),
                engagement(StatutEngagement.CONFIRME),
                engagement(StatutEngagement.TERMINE),
                engagement(StatutEngagement.REJETE)
        ));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.total()).isEqualTo(5);
        assertThat(resultat.parStatut().get(StatutEngagement.EN_ATTENTE)).isEqualTo(1);
        assertThat(resultat.parStatut().get(StatutEngagement.CONFIRME)).isEqualTo(2);
        assertThat(resultat.parStatut().get(StatutEngagement.TERMINE)).isEqualTo(1);
        assertThat(resultat.parStatut().get(StatutEngagement.REJETE)).isEqualTo(1);
    }

    @Test
    void obtenirTableauDeBord_exclut_les_rejetes_du_taux_de_completion() {
        when(engagementRepository.findAll()).thenReturn(List.of(
                engagement(StatutEngagement.TERMINE),
                engagement(StatutEngagement.REJETE),
                engagement(StatutEngagement.REJETE)
        ));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        // 1 termine sur (3 - 2 rejetes) = 1/1 = 100%, pas 1/3
        assertThat(resultat.tauxCompletion()).isEqualTo(1.0);
    }

    @Test
    void obtenirTableauDeBord_compte_un_engagement_confirme_en_retard() {
        Engagement enRetard = engagement(StatutEngagement.CONFIRME);
        enRetard.planifierEcheance(Instant.now().minus(1, ChronoUnit.HOURS));
        Engagement aTemps = engagement(StatutEngagement.CONFIRME);
        aTemps.planifierEcheance(Instant.now().plus(1, ChronoUnit.HOURS));
        when(engagementRepository.findAll()).thenReturn(List.of(enRetard, aTemps));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.enRetard()).isEqualTo(1);
    }

    @Test
    void obtenirTableauDeBord_nignore_pas_les_termines_dans_enRetard_meme_avec_echeance_passee() {
        Engagement termine = engagement(StatutEngagement.TERMINE);
        termine.planifierEcheance(Instant.now().minus(1, ChronoUnit.HOURS));
        when(engagementRepository.findAll()).thenReturn(List.of(termine));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.enRetard()).isZero();
    }

    @Test
    void obtenirTableauDeBord_calcule_le_taux_de_rejet() {
        when(engagementRepository.findAll()).thenReturn(List.of(
                engagement(StatutEngagement.TERMINE),
                engagement(StatutEngagement.REJETE),
                engagement(StatutEngagement.REJETE),
                engagement(StatutEngagement.EN_ATTENTE)
        ));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.tauxRejet()).isEqualTo(0.5);
    }

    @Test
    void obtenirTableauDeBord_delaiMoyenTraitementJours_est_nul_si_aucun_engagement_termine() {
        when(engagementRepository.findAll()).thenReturn(List.of(
                engagement(StatutEngagement.EN_ATTENTE),
                engagement(StatutEngagement.CONFIRME)
        ));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.delaiMoyenTraitementJours()).isNull();
    }

    @Test
    void obtenirTableauDeBord_calcule_le_delai_moyen_de_traitement_sur_les_termines() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        Engagement rapide = engagementAvecDates(StatutEngagement.TERMINE, base, base.plus(2, ChronoUnit.DAYS));
        Engagement lent = engagementAvecDates(StatutEngagement.TERMINE, base, base.plus(6, ChronoUnit.DAYS));
        Engagement nonTermine = engagementAvecDates(StatutEngagement.CONFIRME, base, base.plus(100, ChronoUnit.DAYS));
        when(engagementRepository.findAll()).thenReturn(List.of(rapide, lent, nonTermine));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.delaiMoyenTraitementJours()).isEqualTo(4.0);
    }

    @Test
    void obtenirTableauDeBord_tendanceHebdomadaire_couvre_8_semaines_avec_des_zeros() {
        when(engagementRepository.findAll()).thenReturn(List.of());

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        assertThat(resultat.tendanceHebdomadaire()).hasSize(8);
        assertThat(resultat.tendanceHebdomadaire()).allSatisfy(point -> {
            assertThat(point.crees()).isZero();
            assertThat(point.termines()).isZero();
        });
        LocalDate lundiCourant = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        assertThat(resultat.tendanceHebdomadaire().get(7).debutSemaine()).isEqualTo(lundiCourant);
        assertThat(resultat.tendanceHebdomadaire().get(0).debutSemaine()).isEqualTo(lundiCourant.minusWeeks(7));
    }

    @Test
    void obtenirTableauDeBord_tendanceHebdomadaire_regroupe_crees_et_termines_par_semaine() {
        LocalDate lundiCourant = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        Instant creeCetteSemaine = lundiCourant.atStartOfDay(ZoneOffset.UTC).toInstant().plus(1, ChronoUnit.HOURS);
        Instant termineCetteSemaine = creeCetteSemaine.plus(3, ChronoUnit.HOURS);
        Instant creeSemaineDerniere = lundiCourant.minusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Engagement termineCetteSemaineEngagement = engagementAvecDates(StatutEngagement.TERMINE, creeCetteSemaine, termineCetteSemaine);
        Engagement creeSeulementSemaineDerniere = engagementAvecDates(StatutEngagement.EN_ATTENTE, creeSemaineDerniere, creeSemaineDerniere);
        when(engagementRepository.findAll()).thenReturn(List.of(termineCetteSemaineEngagement, creeSeulementSemaineDerniere));

        TableauDeBordEntrepriseResponse resultat = tableauDeBordEntrepriseService.obtenirTableauDeBord();

        PointTendanceHebdomadaire semaineCourante = resultat.tendanceHebdomadaire().get(7);
        PointTendanceHebdomadaire semainePrecedente = resultat.tendanceHebdomadaire().get(6);
        assertThat(semaineCourante.crees()).isEqualTo(1);
        assertThat(semaineCourante.termines()).isEqualTo(1);
        assertThat(semainePrecedente.crees()).isEqualTo(1);
        assertThat(semainePrecedente.termines()).isZero();
    }
}
