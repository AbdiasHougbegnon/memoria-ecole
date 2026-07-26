package com.memoria.ecole.session;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionService;
import com.memoria.ecole.matiere.Matiere;
import com.memoria.ecole.matiere.MatiereService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContexteScolaireSessionServiceTest {

    @Mock
    private ContexteScolaireSessionRepository contexteScolaireSessionRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private MatiereService matiereService;

    private ContexteScolaireSessionService contexteScolaireSessionService;

    @BeforeEach
    void setUp() {
        contexteScolaireSessionService = new ContexteScolaireSessionService(
                contexteScolaireSessionRepository, sessionService, matiereService);
    }

    @Test
    void rattacherMatiere_cree_le_contexte_si_le_createur_et_le_couloir_correspondent() {
        UUID createurId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Session session = new Session("Cours", createurId, couloirId);
        when(sessionService.obtenirSession(sessionId)).thenReturn(session);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Algo", couloirId, createurId));
        when(contexteScolaireSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        contexteScolaireSessionService.rattacherMatiere(sessionId, matiereId, createurId);

        verify(contexteScolaireSessionRepository).save(org.mockito.ArgumentMatchers.argThat(
                c -> c.getSessionId().equals(sessionId) && c.getMatiereId().equals(matiereId)));
    }

    @Test
    void rattacherMatiere_remplace_le_contexte_existant_plutot_que_d_en_creer_un_second() {
        UUID createurId = UUID.randomUUID();
        UUID couloirId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID nouvelleMatiereId = UUID.randomUUID();
        Session session = new Session("Cours", createurId, couloirId);
        ContexteScolaireSession existant = new ContexteScolaireSession(sessionId, UUID.randomUUID());
        when(sessionService.obtenirSession(sessionId)).thenReturn(session);
        when(matiereService.obtenirMatiere(nouvelleMatiereId)).thenReturn(new Matiere("Algo", couloirId, createurId));
        when(contexteScolaireSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existant));

        contexteScolaireSessionService.rattacherMatiere(sessionId, nouvelleMatiereId, createurId);

        assertThat(existant.getMatiereId()).isEqualTo(nouvelleMatiereId);
        verify(contexteScolaireSessionRepository).save(existant);
    }

    @Test
    void rattacherMatiere_leve_une_exception_si_pas_createur_de_la_session() {
        UUID createurId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Session session = new Session("Cours", createurId, UUID.randomUUID());
        when(sessionService.obtenirSession(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> contexteScolaireSessionService.rattacherMatiere(sessionId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PasCreateurDeLaSessionException.class);
        verify(contexteScolaireSessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rattacherMatiere_leve_une_exception_si_la_matiere_n_appartient_pas_au_couloir_de_la_session() {
        UUID createurId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID matiereId = UUID.randomUUID();
        Session session = new Session("Cours", createurId, UUID.randomUUID());
        when(sessionService.obtenirSession(sessionId)).thenReturn(session);
        when(matiereService.obtenirMatiere(matiereId)).thenReturn(new Matiere("Algo", UUID.randomUUID(), createurId));

        assertThatThrownBy(() -> contexteScolaireSessionService.rattacherMatiere(sessionId, matiereId, createurId))
                .isInstanceOf(MatiereIncompatibleAvecSessionException.class);
        verify(contexteScolaireSessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
