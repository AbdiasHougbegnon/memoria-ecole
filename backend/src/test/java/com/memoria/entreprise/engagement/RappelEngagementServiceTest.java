package com.memoria.entreprise.engagement;

import com.memoria.core.auth.ModuleMemoria;
import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.email.EnvoyeurEmail;
import com.memoria.core.session.SessionService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RappelEngagementServiceTest {

    @Mock
    private EngagementRepository engagementRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private EnvoyeurEmail envoyeurEmail;

    private RappelEngagementService rappelEngagementService;

    @BeforeEach
    void setUp() {
        rappelEngagementService = new RappelEngagementService(
                engagementRepository, sessionService, utilisateurRepository, envoyeurEmail);
    }

    private Engagement engagementConfirme(UUID sessionId, Instant echeance) {
        Engagement engagement = new Engagement(sessionId, "Envoyer le mail", "Intervenant 1", "vendredi");
        engagement.confirmer();
        engagement.planifierEcheance(echeance);
        return engagement;
    }

    @Test
    void verifierEcheances_envoie_un_rappel_pour_une_echeance_proche_et_le_marque_envoye() {
        UUID sessionId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().plus(2, ChronoUnit.HOURS));
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(sessionService.resoudreEmailsParticipants(sessionId)).thenReturn(List.of("createur@test.fr"));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail).envoyer(eq("createur@test.fr"), anyString(), anyString());
        assertThat(engagement.isRappelEcheanceProcheEnvoye()).isTrue();
        assertThat(engagement.isRappelRetardEnvoye()).isFalse();
    }

    @Test
    void verifierEcheances_envoie_un_rappel_pour_une_echeance_depassee_et_le_marque_envoye() {
        UUID sessionId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().minus(3, ChronoUnit.HOURS));
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(sessionService.resoudreEmailsParticipants(sessionId)).thenReturn(List.of("createur@test.fr"));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail).envoyer(eq("createur@test.fr"), anyString(), anyString());
        assertThat(engagement.isRappelRetardEnvoye()).isTrue();
    }

    @Test
    void verifierEcheances_cible_precisement_le_responsable_identifie() {
        UUID sessionId = UUID.randomUUID();
        UUID responsableId = UUID.randomUUID();
        Engagement engagement = new Engagement(sessionId, "Envoyer le mail", "Alice Martin", "vendredi", responsableId);
        engagement.confirmer();
        engagement.planifierEcheance(Instant.now().minus(1, ChronoUnit.HOURS));
        Utilisateur responsable = new Utilisateur("alice@test.fr", "hash", ModuleMemoria.ENTREPRISE);
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(utilisateurRepository.findById(responsableId)).thenReturn(java.util.Optional.of(responsable));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail).envoyer(eq("alice@test.fr"), anyString(), anyString());
        verify(sessionService, never()).resoudreEmailsParticipants(any());
    }

    @Test
    void verifierEcheances_nenvoie_rien_si_deja_envoye() {
        UUID sessionId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().plus(2, ChronoUnit.HOURS));
        engagement.marquerRappelEcheanceProcheEnvoye();
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail, never()).envoyer(anyString(), anyString(), anyString());
        verify(sessionService, never()).resoudreEmailsParticipants(any());
    }

    @Test
    void verifierEcheances_nenvoie_rien_si_lecheance_est_hors_fenetre() {
        UUID sessionId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().plus(3, ChronoUnit.DAYS));
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail, never()).envoyer(anyString(), anyString(), anyString());
    }

    @Test
    void verifierEcheances_ne_fait_rien_si_aucun_destinataire_resolu() {
        UUID sessionId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().minus(1, ChronoUnit.HOURS));
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(sessionService.resoudreEmailsParticipants(sessionId)).thenReturn(List.of());

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail, never()).envoyer(anyString(), anyString(), anyString());
        verify(engagementRepository, never()).save(any());
    }
}
