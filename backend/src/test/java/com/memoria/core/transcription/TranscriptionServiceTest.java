package com.memoria.core.transcription;

import com.memoria.core.audio.ChunkAudioEnregistreEvent;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private TranscripteurPort transcripteur;

    @Mock
    private SessionService sessionService;

    private TranscriptionService transcriptionService;

    @BeforeEach
    void setUp() {
        transcriptionService = new TranscriptionService(transcriptionRepository, transcripteur, sessionService);
    }

    @Test
    void surChunkEnregistre_sauvegarde_le_texte_reconnu_quand_la_transcription_reussit() {
        UUID sessionId = UUID.randomUUID();
        ChunkAudioEnregistreEvent evenement = new ChunkAudioEnregistreEvent(sessionId, 0, new byte[]{1, 2, 3});
        when(transcripteur.transcrire(evenement.donnees())).thenReturn("Bonjour tout le monde");

        transcriptionService.surChunkEnregistre(evenement);

        ArgumentCaptor<Transcription> captor = ArgumentCaptor.forClass(Transcription.class);
        verify(transcriptionRepository).save(captor.capture());
        Transcription transcription = captor.getValue();
        assertThat(transcription.getSessionId()).isEqualTo(sessionId);
        assertThat(transcription.getNumeroSequence()).isEqualTo(0);
        assertThat(transcription.getTexte()).isEqualTo("Bonjour tout le monde");
        assertThat(transcription.getStatut()).isEqualTo(TranscriptionStatut.REUSSIE);
    }

    @Test
    void surChunkEnregistre_marque_echec_sans_texte_quand_le_transcripteur_echoue() {
        UUID sessionId = UUID.randomUUID();
        ChunkAudioEnregistreEvent evenement = new ChunkAudioEnregistreEvent(sessionId, 1, new byte[]{1});
        when(transcripteur.transcrire(any())).thenThrow(new TranscriptionException("Azure indisponible"));

        transcriptionService.surChunkEnregistre(evenement);

        ArgumentCaptor<Transcription> captor = ArgumentCaptor.forClass(Transcription.class);
        verify(transcriptionRepository).save(captor.capture());
        Transcription transcription = captor.getValue();
        assertThat(transcription.getStatut()).isEqualTo(TranscriptionStatut.ECHEC);
        assertThat(transcription.getTexte()).isNull();
    }

    @Test
    void obtenirTranscriptions_retourne_les_segments_de_la_session_tries() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        List<Transcription> segments = List.of(new Transcription(sessionId, 0, "Un", TranscriptionStatut.REUSSIE));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(segments);

        List<Transcription> resultat = transcriptionService.obtenirTranscriptions(sessionId);

        assertThat(resultat).isEqualTo(segments);
    }

    @Test
    void obtenirTranscriptions_leve_une_exception_si_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionService.obtenirSession(idInconnu)).thenThrow(new SessionNotFoundException(idInconnu));

        assertThatThrownBy(() -> transcriptionService.obtenirTranscriptions(idInconnu))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
