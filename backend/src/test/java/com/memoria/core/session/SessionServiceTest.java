package com.memoria.core.session;

import com.memoria.core.couloir.MembreCouloirRepository;
import com.memoria.core.couloir.PasMembreDuCouloirException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MembreCouloirRepository membreCouloirRepository;

    private SessionService sessionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, eventPublisher, membreCouloirRepository);
    }

    @Test
    void creerSession_sauvegarde_une_session_en_cours_avec_le_titre_donne() {
        when(sessionRepository.save(org.mockito.ArgumentMatchers.any(Session.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Session session = sessionService.creerSession("Reunion hebdomadaire");

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());

        assertThat(captor.getValue()).isSameAs(session);
        assertThat(session.getId()).isNotNull();
        assertThat(session.getTitre()).isEqualTo("Reunion hebdomadaire");
        assertThat(session.getDateCreation()).isNotNull();
        assertThat(session.getStatut()).isEqualTo(SessionStatus.EN_COURS);
    }

    @Test
    void obtenirSession_retourne_la_session_quand_elle_existe() {
        Session session = new Session("Cours de reseaux");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        Session resultat = sessionService.obtenirSession(session.getId());

        assertThat(resultat).isSameAs(session);
    }

    @Test
    void obtenirSession_leve_une_exception_quand_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionRepository.findById(idInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.obtenirSession(idInconnu))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void terminerSession_fait_passer_le_statut_a_terminee_et_publie_un_evenement() {
        Session session = new Session("Cours de reseaux");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        Session resultat = sessionService.terminerSession(session.getId());

        assertThat(resultat.getStatut()).isEqualTo(SessionStatus.TERMINEE);
        verify(eventPublisher).publishEvent(new SessionTermineeEvent(session.getId()));
    }

    @Test
    void terminerSession_est_idempotent_et_ne_republie_pas_levenement_si_deja_terminee() {
        Session session = new Session("Cours de reseaux");
        session.terminer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        Session resultat = sessionService.terminerSession(session.getId());

        assertThat(resultat.getStatut()).isEqualTo(SessionStatus.TERMINEE);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void terminerSession_leve_une_exception_quand_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionRepository.findById(idInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.terminerSession(idInconnu))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void listerSessions_retourne_les_sessions_triees_par_date_de_creation_decroissante() {
        List<Session> sessions = List.of(new Session("Recente"), new Session("Ancienne"));
        when(sessionRepository.findAllByOrderByDateCreationDesc()).thenReturn(sessions);

        List<Session> resultat = sessionService.listerSessions();

        assertThat(resultat).isEqualTo(sessions);
    }

    @Test
    void creerSession_avec_couloir_rattache_la_session_si_lutilisateur_en_est_membre() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, utilisateurId)).thenReturn(true);
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session session = sessionService.creerSession("Cours de reseaux", couloirId, utilisateurId);

        assertThat(session.getCouloirId()).isEqualTo(couloirId);
    }

    @Test
    void creerSession_avec_couloir_leve_une_exception_si_lutilisateur_nest_pas_membre() {
        UUID couloirId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(membreCouloirRepository.existsByCouloirIdAndUtilisateurId(couloirId, utilisateurId)).thenReturn(false);

        assertThatThrownBy(() -> sessionService.creerSession("Cours de reseaux", couloirId, utilisateurId))
                .isInstanceOf(PasMembreDuCouloirException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void creerSession_avec_createur_enregistre_le_createur() {
        UUID utilisateurId = UUID.randomUUID();
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session session = sessionService.creerSession("Cours de reseaux", utilisateurId);

        assertThat(session.getCreateurId()).isEqualTo(utilisateurId);
        assertThat(session.getCouloirId()).isNull();
    }

    @Test
    void listerSessionsVisibles_utilise_findVisiblesPour_si_lutilisateur_a_des_couloirs() {
        UUID utilisateurId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        com.memoria.core.couloir.MembreCouloir membre = new com.memoria.core.couloir.MembreCouloir(couloirId, utilisateurId);
        when(membreCouloirRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of(membre));
        List<Session> visibles = List.of(new Session("Visible"));
        when(sessionRepository.findVisiblesPour(utilisateurId, List.of(couloirId))).thenReturn(visibles);

        List<Session> resultat = sessionService.listerSessionsVisibles(utilisateurId);

        assertThat(resultat).isEqualTo(visibles);
        verify(sessionRepository, never()).findByCreateurIdOrCreateurIdIsNullOrderByDateCreationDesc(any());
    }

    @Test
    void listerSessionsVisibles_utilise_la_requete_sans_couloir_si_lutilisateur_nen_a_aucun() {
        UUID utilisateurId = UUID.randomUUID();
        when(membreCouloirRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of());
        List<Session> visibles = List.of(new Session("Visible"));
        when(sessionRepository.findByCreateurIdOrCreateurIdIsNullOrderByDateCreationDesc(utilisateurId)).thenReturn(visibles);

        List<Session> resultat = sessionService.listerSessionsVisibles(utilisateurId);

        assertThat(resultat).isEqualTo(visibles);
        verify(sessionRepository, never()).findVisiblesPour(any(), any());
    }
}
