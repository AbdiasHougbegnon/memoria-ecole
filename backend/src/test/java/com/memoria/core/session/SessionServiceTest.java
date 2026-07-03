package com.memoria.core.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionService sessionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository);
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
    void terminerSession_fait_passer_le_statut_a_terminee() {
        Session session = new Session("Cours de reseaux");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        Session resultat = sessionService.terminerSession(session.getId());

        assertThat(resultat.getStatut()).isEqualTo(SessionStatus.TERMINEE);
    }

    @Test
    void terminerSession_est_idempotent_si_deja_terminee() {
        Session session = new Session("Cours de reseaux");
        session.terminer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        Session resultat = sessionService.terminerSession(session.getId());

        assertThat(resultat.getStatut()).isEqualTo(SessionStatus.TERMINEE);
    }

    @Test
    void terminerSession_leve_une_exception_quand_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionRepository.findById(idInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.terminerSession(idInconnu))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
