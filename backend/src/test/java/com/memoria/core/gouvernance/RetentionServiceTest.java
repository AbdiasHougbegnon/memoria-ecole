package com.memoria.core.gouvernance;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private SessionPurgeService sessionPurgeService;
    @Mock private JournalRgpdRepository journalRgpdRepository;

    @Test
    void purgerSessionsExpirees_ne_fait_rien_si_la_retention_est_desactivee() {
        RetentionService retentionService = new RetentionService(sessionRepository, sessionPurgeService, journalRgpdRepository, -1);

        retentionService.purgerSessionsExpirees();

        verify(sessionRepository, never()).findByDateCreationBefore(any());
        verify(journalRgpdRepository, never()).save(any());
    }

    @Test
    void purgerSessionsExpirees_purge_les_sessions_plus_vieilles_que_le_seuil_et_journalise() {
        RetentionService retentionService = new RetentionService(sessionRepository, sessionPurgeService, journalRgpdRepository, 30);
        Session session1 = new Session("Vieille session 1");
        Session session2 = new Session("Vieille session 2");
        when(sessionRepository.findByDateCreationBefore(any(Instant.class))).thenReturn(List.of(session1, session2));

        retentionService.purgerSessionsExpirees();

        verify(sessionPurgeService).purgerSessionCompletement(session1.getId());
        verify(sessionPurgeService).nettoyerDependancesExternes(session1.getId());
        verify(sessionPurgeService).purgerSessionCompletement(session2.getId());
        verify(sessionPurgeService).nettoyerDependancesExternes(session2.getId());

        ArgumentCaptor<JournalRgpd> captor = ArgumentCaptor.forClass(JournalRgpd.class);
        verify(journalRgpdRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TypeActionRgpd.PURGE_RETENTION);
        assertThat(captor.getValue().getUtilisateurCibleId()).isNull();
    }

    @Test
    void purgerSessionsExpirees_ne_journalise_rien_si_aucune_session_nest_expiree() {
        RetentionService retentionService = new RetentionService(sessionRepository, sessionPurgeService, journalRgpdRepository, 30);
        when(sessionRepository.findByDateCreationBefore(any(Instant.class))).thenReturn(List.of());

        retentionService.purgerSessionsExpirees();

        verify(journalRgpdRepository, never()).save(any());
    }

    @Test
    void purgerSessionsExpirees_continue_apres_lechec_dune_session() {
        RetentionService retentionService = new RetentionService(sessionRepository, sessionPurgeService, journalRgpdRepository, 30);
        Session sessionEnErreur = new Session("En erreur");
        Session sessionOk = new Session("Ok");
        when(sessionRepository.findByDateCreationBefore(any(Instant.class))).thenReturn(List.of(sessionEnErreur, sessionOk));
        org.mockito.Mockito.doThrow(new RuntimeException("base indisponible"))
                .when(sessionPurgeService).purgerSessionCompletement(sessionEnErreur.getId());

        retentionService.purgerSessionsExpirees();

        verify(sessionPurgeService, never()).nettoyerDependancesExternes(sessionEnErreur.getId());
        verify(sessionPurgeService).purgerSessionCompletement(sessionOk.getId());
        verify(sessionPurgeService).nettoyerDependancesExternes(sessionOk.getId());

        ArgumentCaptor<JournalRgpd> captor = ArgumentCaptor.forClass(JournalRgpd.class);
        verify(journalRgpdRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).contains("1 session");
    }
}
