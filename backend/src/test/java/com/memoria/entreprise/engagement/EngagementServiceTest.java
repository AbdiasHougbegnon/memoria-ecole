package com.memoria.entreprise.engagement;

import com.memoria.core.email.EnvoyeurEmail;
import com.memoria.core.session.SessionService;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngagementServiceTest {

    @Mock
    private EngagementRepository engagementRepository;

    @Mock
    private CompteRenduRepository compteRenduRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private EnvoyeurEmail envoyeurEmail;

    private EngagementService engagementService;

    @BeforeEach
    void setUp() {
        engagementService = new EngagementService(engagementRepository, compteRenduRepository, sessionService, envoyeurEmail);
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
    void terminer_fait_passer_un_engagement_confirme_a_termine_et_notifie_les_participants() {
        UUID sessionId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Engagement engagement = new Engagement(sessionId, "Envoyer le mail", null, null);
        engagement.confirmer();
        when(engagementRepository.findById(id)).thenReturn(Optional.of(engagement));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionService.resoudreEmailsParticipants(sessionId)).thenReturn(List.of("createur@test.fr"));

        Engagement resultat = engagementService.terminer(id);

        assertThat(resultat.getStatut()).isEqualTo(StatutEngagement.TERMINE);
        verify(envoyeurEmail).envoyer(eq("createur@test.fr"), anyString(), anyString());
    }

    @Test
    void terminer_nenvoie_rien_si_aucun_destinataire_resolu() {
        UUID sessionId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Engagement engagement = new Engagement(sessionId, "Envoyer le mail", null, null);
        engagement.confirmer();
        when(engagementRepository.findById(id)).thenReturn(Optional.of(engagement));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionService.resoudreEmailsParticipants(sessionId)).thenReturn(List.of());

        engagementService.terminer(id);

        verify(envoyeurEmail, never()).envoyer(anyString(), anyString(), anyString());
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

    @Test
    void planifierEcheance_enregistre_la_date_et_reinitialise_les_rappels_deja_envoyes() {
        UUID id = UUID.randomUUID();
        Engagement engagement = new Engagement(UUID.randomUUID(), "Envoyer le mail", null, null);
        engagement.planifierEcheance(Instant.parse("2026-01-01T00:00:00Z"));
        engagement.marquerRappelEcheanceProcheEnvoye();
        engagement.marquerRappelRetardEnvoye();
        when(engagementRepository.findById(id)).thenReturn(Optional.of(engagement));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Instant nouvelleEcheance = Instant.parse("2026-02-01T00:00:00Z");

        Engagement resultat = engagementService.planifierEcheance(id, nouvelleEcheance);

        assertThat(resultat.getDateEcheance()).isEqualTo(nouvelleEcheance);
        assertThat(resultat.isRappelEcheanceProcheEnvoye()).isFalse();
        assertThat(resultat.isRappelRetardEnvoye()).isFalse();
    }
}
