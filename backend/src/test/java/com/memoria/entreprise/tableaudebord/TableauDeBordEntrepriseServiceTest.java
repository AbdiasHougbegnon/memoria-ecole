package com.memoria.entreprise.tableaudebord;

import com.memoria.entreprise.engagement.Engagement;
import com.memoria.entreprise.engagement.EngagementRepository;
import com.memoria.entreprise.engagement.StatutEngagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}
