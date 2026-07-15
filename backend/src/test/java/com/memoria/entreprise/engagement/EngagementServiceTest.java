package com.memoria.entreprise.engagement;

import com.memoria.entreprise.compterendu.ActionCompteRendu;
import com.memoria.entreprise.compterendu.CompteRendu;
import com.memoria.entreprise.compterendu.CompteRenduGenereEvent;
import com.memoria.entreprise.compterendu.CompteRenduRepository;
import com.memoria.entreprise.compterendu.StatutCompteRendu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngagementServiceTest {

    @Mock
    private EngagementRepository engagementRepository;

    @Mock
    private CompteRenduRepository compteRenduRepository;

    private EngagementService engagementService;

    @BeforeEach
    void setUp() {
        engagementService = new EngagementService(engagementRepository, compteRenduRepository);
    }

    @Test
    void surCompteRenduGenere_cree_un_engagement_en_attente_par_action() {
        UUID sessionId = UUID.randomUUID();
        CompteRendu compteRendu = new CompteRendu(
                sessionId, "Synthese", List.of("Decision X"),
                List.of(
                        new ActionCompteRendu("Envoyer le mail", "Intervenant 2", "vendredi"),
                        new ActionCompteRendu("Relire le contrat", null, null)
                ),
                List.of(0), StatutCompteRendu.REUSSI
        );
        when(engagementRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.of(compteRendu));

        engagementService.surCompteRenduGenere(new CompteRenduGenereEvent(sessionId));

        ArgumentCaptor<List<Engagement>> captor = ArgumentCaptor.forClass(List.class);
        verify(engagementRepository).saveAll(captor.capture());
        List<Engagement> engagements = captor.getValue();
        assertThat(engagements).hasSize(2);
        assertThat(engagements.get(0).getDescription()).isEqualTo("Envoyer le mail");
        assertThat(engagements.get(0).getResponsable()).isEqualTo("Intervenant 2");
        assertThat(engagements.get(0).getEcheance()).isEqualTo("vendredi");
        assertThat(engagements.get(0).getStatut()).isEqualTo(StatutEngagement.EN_ATTENTE);
        assertThat(engagements.get(1).getDescription()).isEqualTo("Relire le contrat");
        assertThat(engagements.get(1).getResponsable()).isNull();
    }

    @Test
    void surCompteRenduGenere_ignore_les_actions_a_description_vide() {
        UUID sessionId = UUID.randomUUID();
        CompteRendu compteRendu = new CompteRendu(
                sessionId, "Synthese", List.of(),
                List.of(new ActionCompteRendu("  ", null, null)),
                List.of(0), StatutCompteRendu.REUSSI
        );
        when(engagementRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.of(compteRendu));

        engagementService.surCompteRenduGenere(new CompteRenduGenereEvent(sessionId));

        verify(engagementRepository, never()).saveAll(any());
    }

    @Test
    void surCompteRenduGenere_ne_fait_rien_si_deja_traite() {
        UUID sessionId = UUID.randomUUID();
        when(engagementRepository.existsBySessionId(sessionId)).thenReturn(true);

        engagementService.surCompteRenduGenere(new CompteRenduGenereEvent(sessionId));

        verify(compteRenduRepository, never()).findBySessionId(any());
        verify(engagementRepository, never()).saveAll(any());
    }

    @Test
    void surCompteRenduGenere_ne_fait_rien_si_le_compte_rendu_a_echoue() {
        UUID sessionId = UUID.randomUUID();
        CompteRendu compteRendu = new CompteRendu(
                sessionId, null, List.of(), List.of(), List.of(0), StatutCompteRendu.ECHEC
        );
        when(engagementRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(compteRenduRepository.findBySessionId(sessionId)).thenReturn(Optional.of(compteRendu));

        engagementService.surCompteRenduGenere(new CompteRenduGenereEvent(sessionId));

        verify(engagementRepository, never()).saveAll(any());
    }

    @Test
    void confirmer_fait_passer_un_engagement_en_attente_a_confirme() {
        UUID id = UUID.randomUUID();
        Engagement engagement = new Engagement(UUID.randomUUID(), "Envoyer le mail", null, null);
        when(engagementRepository.findById(id)).thenReturn(Optional.of(engagement));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Engagement resultat = engagementService.confirmer(id);

        assertThat(resultat.getStatut()).isEqualTo(StatutEngagement.CONFIRME);
    }

    @Test
    void rejeter_fait_passer_un_engagement_en_attente_a_rejete() {
        UUID id = UUID.randomUUID();
        Engagement engagement = new Engagement(UUID.randomUUID(), "Envoyer le mail", null, null);
        when(engagementRepository.findById(id)).thenReturn(Optional.of(engagement));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Engagement resultat = engagementService.rejeter(id);

        assertThat(resultat.getStatut()).isEqualTo(StatutEngagement.REJETE);
    }

    @Test
    void terminer_leve_une_exception_si_lengagement_nest_pas_confirme() {
        UUID id = UUID.randomUUID();
        Engagement engagement = new Engagement(UUID.randomUUID(), "Envoyer le mail", null, null);
        when(engagementRepository.findById(id)).thenReturn(Optional.of(engagement));

        assertThatThrownBy(() -> engagementService.terminer(id))
                .isInstanceOf(TransitionEngagementInvalideException.class);
    }

    @Test
    void confirmer_leve_une_exception_si_lengagement_est_introuvable() {
        UUID id = UUID.randomUUID();
        when(engagementRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engagementService.confirmer(id))
                .isInstanceOf(EngagementNotFoundException.class);
    }
}
