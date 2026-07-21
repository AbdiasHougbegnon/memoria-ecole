package com.memoria.entreprise.engagement;

import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.couloir.MembreCouloir;
import com.memoria.core.couloir.MembreCouloirRepository;
import com.memoria.core.email.EnvoyeurEmail;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private SessionRepository sessionRepository;

    @Mock
    private MembreCouloirRepository membreCouloirRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private EnvoyeurEmail envoyeurEmail;

    private RappelEngagementService rappelEngagementService;

    @BeforeEach
    void setUp() {
        rappelEngagementService = new RappelEngagementService(
                engagementRepository, sessionRepository, membreCouloirRepository, utilisateurRepository, envoyeurEmail);
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
        UUID createurId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().plus(2, ChronoUnit.HOURS));
        Session session = new Session("Cours", createurId, null);
        Utilisateur utilisateur = new Utilisateur("createur@test.fr", "hash");
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(utilisateurRepository.findById(createurId)).thenReturn(Optional.of(utilisateur));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail).envoyer(eq("createur@test.fr"), anyString(), anyString());
        org.assertj.core.api.Assertions.assertThat(engagement.isRappelEcheanceProcheEnvoye()).isTrue();
        org.assertj.core.api.Assertions.assertThat(engagement.isRappelRetardEnvoye()).isFalse();
    }

    @Test
    void verifierEcheances_envoie_un_rappel_pour_une_echeance_depassee_et_le_marque_envoye() {
        UUID sessionId = UUID.randomUUID();
        UUID createurId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().minus(3, ChronoUnit.HOURS));
        Session session = new Session("Cours", createurId, null);
        Utilisateur utilisateur = new Utilisateur("createur@test.fr", "hash");
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(utilisateurRepository.findById(createurId)).thenReturn(Optional.of(utilisateur));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail).envoyer(eq("createur@test.fr"), anyString(), anyString());
        org.assertj.core.api.Assertions.assertThat(engagement.isRappelRetardEnvoye()).isTrue();
    }

    @Test
    void verifierEcheances_nenvoie_rien_si_deja_envoye() {
        UUID sessionId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().plus(2, ChronoUnit.HOURS));
        engagement.marquerRappelEcheanceProcheEnvoye();
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail, never()).envoyer(anyString(), anyString(), anyString());
        verify(sessionRepository, never()).findById(any());
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
    void verifierEcheances_resout_les_destinataires_via_le_couloir_de_la_session() {
        UUID sessionId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID membreId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().minus(1, ChronoUnit.HOURS));
        Session session = new Session("Cours", null, couloirId);
        Utilisateur membre = new Utilisateur("membre@test.fr", "hash");
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(membreCouloirRepository.findByCouloirId(couloirId)).thenReturn(List.of(new MembreCouloir(couloirId, membreId)));
        when(utilisateurRepository.findById(membreId)).thenReturn(Optional.of(membre));
        when(engagementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail).envoyer(eq("membre@test.fr"), anyString(), anyString());
    }

    @Test
    void verifierEcheances_ne_fait_rien_si_aucun_destinataire_resolu() {
        UUID sessionId = UUID.randomUUID();
        Engagement engagement = engagementConfirme(sessionId, Instant.now().minus(1, ChronoUnit.HOURS));
        Session session = new Session("Cours", null, null);
        when(engagementRepository.findByStatutAndDateEcheanceNotNull(StatutEngagement.CONFIRME)).thenReturn(List.of(engagement));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        rappelEngagementService.verifierEcheances();

        verify(envoyeurEmail, never()).envoyer(anyString(), anyString(), anyString());
        verify(engagementRepository, never()).save(any());
    }
}
