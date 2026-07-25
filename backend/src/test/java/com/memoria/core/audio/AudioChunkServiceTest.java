package com.memoria.core.audio;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioChunkServiceTest {

    @Mock
    private AudioChunkRepository audioChunkRepository;

    @Mock
    private StockageAudioPort stockageAudio;

    @Mock
    private SessionService sessionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AudioChunkService audioChunkService;

    @BeforeEach
    void setUp() {
        audioChunkService = new AudioChunkService(audioChunkRepository, stockageAudio, sessionService, eventPublisher);
    }

    @Test
    void enregistrerChunk_stocke_le_binaire_et_sauvegarde_les_metadonnees_quand_nouveau() {
        Session session = new Session("Cours de reseaux");
        when(sessionService.obtenirSession(session.getId())).thenReturn(session);
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(session.getId(), 0)).thenReturn(Optional.empty());
        when(stockageAudio.sauvegarder(any(), anyInt(), any())).thenReturn("/data/audio/x/0.chunk");
        when(audioChunkRepository.save(any(AudioChunk.class))).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] donnees = {1, 2, 3};
        ResultatEnregistrementChunk resultat = audioChunkService.enregistrerChunk(session.getId(), 0, donnees);

        verify(stockageAudio).sauvegarder(session.getId(), 0, donnees);
        assertThat(resultat.dejaRecu()).isFalse();
        assertThat(resultat.chunk().getSessionId()).isEqualTo(session.getId());
        assertThat(resultat.chunk().getNumeroSequence()).isEqualTo(0);
        assertThat(resultat.chunk().getCheminStockage()).isEqualTo("/data/audio/x/0.chunk");
        assertThat(resultat.chunk().getTailleOctets()).isEqualTo(3);
        verify(eventPublisher).publishEvent(new ChunkAudioEnregistreEvent(session.getId(), 0, donnees));
    }

    @Test
    void enregistrerChunk_est_idempotent_quand_le_chunk_a_deja_ete_recu() {
        Session session = new Session("Cours de reseaux");
        AudioChunk chunkExistant = new AudioChunk(session.getId(), 0, "/data/audio/x/0.chunk", 3);
        when(sessionService.obtenirSession(session.getId())).thenReturn(session);
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(session.getId(), 0)).thenReturn(Optional.of(chunkExistant));

        ResultatEnregistrementChunk resultat = audioChunkService.enregistrerChunk(session.getId(), 0, new byte[]{1, 2, 3});

        assertThat(resultat.dejaRecu()).isTrue();
        assertThat(resultat.chunk()).isSameAs(chunkExistant);
        verify(stockageAudio, never()).sauvegarder(any(), anyInt(), any());
        verify(audioChunkRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void enregistrerChunk_leve_une_exception_quand_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionService.obtenirSession(idInconnu)).thenThrow(new SessionNotFoundException(idInconnu));

        assertThatThrownBy(() -> audioChunkService.enregistrerChunk(idInconnu, 0, new byte[]{1}))
                .isInstanceOf(SessionNotFoundException.class);
        verify(stockageAudio, never()).sauvegarder(any(), anyInt(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void enregistrerChunk_leve_une_exception_quand_la_session_nest_plus_en_cours() {
        Session session = mock(Session.class);
        UUID sessionId = UUID.randomUUID();
        when(session.getStatut()).thenReturn(SessionStatus.TERMINEE);
        when(sessionService.obtenirSession(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> audioChunkService.enregistrerChunk(sessionId, 0, new byte[]{1}))
                .isInstanceOf(SessionNonActiveException.class);
        verify(stockageAudio, never()).sauvegarder(any(), anyInt(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void listerNumerosRecus_retourne_les_numeros_tries_du_repository() {
        Session session = new Session("Cours de reseaux");
        when(sessionService.obtenirSession(session.getId())).thenReturn(session);
        when(audioChunkRepository.findNumerosSequenceBySessionId(session.getId())).thenReturn(List.of(0, 1, 2));

        List<Integer> resultat = audioChunkService.listerNumerosRecus(session.getId());

        assertThat(resultat).containsExactly(0, 1, 2);
    }

    @Test
    void listerNumerosRecus_leve_une_exception_quand_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionService.obtenirSession(idInconnu)).thenThrow(new SessionNotFoundException(idInconnu));

        assertThatThrownBy(() -> audioChunkService.listerNumerosRecus(idInconnu))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void obtenirAudio_relit_le_chunk_via_le_chemin_de_stockage() {
        UUID sessionId = UUID.randomUUID();
        AudioChunk chunk = new AudioChunk(sessionId, 2, "/data/audio/x/2.chunk", 3);
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, 2)).thenReturn(Optional.of(chunk));
        when(stockageAudio.lire("/data/audio/x/2.chunk")).thenReturn(new byte[]{4, 5, 6});

        byte[] resultat = audioChunkService.obtenirAudio(sessionId, 2);

        assertThat(resultat).containsExactly(4, 5, 6);
    }

    @Test
    void obtenirAudio_leve_une_exception_quand_le_chunk_est_introuvable() {
        UUID sessionId = UUID.randomUUID();
        when(audioChunkRepository.findBySessionIdAndNumeroSequence(sessionId, 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> audioChunkService.obtenirAudio(sessionId, 9))
                .isInstanceOf(AudioChunkNotFoundException.class);
        verify(stockageAudio, never()).lire(any());
    }
}
