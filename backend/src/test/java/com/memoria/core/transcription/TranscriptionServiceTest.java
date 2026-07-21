package com.memoria.core.transcription;

import com.memoria.core.audio.AudioChunkRepository;
import com.memoria.core.audio.ChunkAudioEnregistreEvent;
import com.memoria.core.auth.Utilisateur;
import com.memoria.core.auth.UtilisateurRepository;
import com.memoria.core.session.Session;
import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private AudioChunkRepository audioChunkRepository;

    @Mock
    private TranscripteurPort transcripteur;

    @Mock
    private SessionService sessionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    private TranscriptionService transcriptionService;

    @BeforeEach
    void setUp() {
        transcriptionService = new TranscriptionService(
                transcriptionRepository, audioChunkRepository, transcripteur, sessionService, eventPublisher, utilisateurRepository
        );
    }

    @Test
    void surChunkEnregistre_sauvegarde_le_texte_et_les_segments_locuteur_quand_la_transcription_reussit() {
        UUID sessionId = UUID.randomUUID();
        ChunkAudioEnregistreEvent evenement = new ChunkAudioEnregistreEvent(sessionId, 0, new byte[]{1, 2, 3});
        List<SegmentLocuteur> segments = List.of(
                new SegmentLocuteur(1, "Bonjour", 0, 500),
                new SegmentLocuteur(2, "tout le monde", 500, 700)
        );
        when(transcripteur.transcrire(evenement.donnees()))
                .thenReturn(new ResultatTranscription("Bonjour tout le monde", segments));
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));

        transcriptionService.surChunkEnregistre(evenement);

        ArgumentCaptor<Transcription> captor = ArgumentCaptor.forClass(Transcription.class);
        verify(transcriptionRepository).save(captor.capture());
        Transcription transcription = captor.getValue();
        assertThat(transcription.getSessionId()).isEqualTo(sessionId);
        assertThat(transcription.getNumeroSequence()).isEqualTo(0);
        assertThat(transcription.getTexte()).isEqualTo("Bonjour tout le monde");
        assertThat(transcription.getStatut()).isEqualTo(TranscriptionStatut.REUSSIE);
        assertThat(transcription.getSegmentsLocuteur()).isEqualTo(segments);
    }

    @Test
    void surChunkEnregistre_marque_echec_sans_texte_quand_le_transcripteur_echoue() {
        UUID sessionId = UUID.randomUUID();
        ChunkAudioEnregistreEvent evenement = new ChunkAudioEnregistreEvent(sessionId, 1, new byte[]{1});
        when(transcripteur.transcrire(any())).thenThrow(new TranscriptionException("Azure indisponible"));
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));

        transcriptionService.surChunkEnregistre(evenement);

        ArgumentCaptor<Transcription> captor = ArgumentCaptor.forClass(Transcription.class);
        verify(transcriptionRepository).save(captor.capture());
        Transcription transcription = captor.getValue();
        assertThat(transcription.getStatut()).isEqualTo(TranscriptionStatut.ECHEC);
        assertThat(transcription.getTexte()).isNull();
        assertThat(transcription.getSegmentsLocuteur()).isEmpty();
    }

    @Test
    void surChunkEnregistre_ne_publie_rien_si_la_session_est_encore_en_cours() {
        UUID sessionId = UUID.randomUUID();
        ChunkAudioEnregistreEvent evenement = new ChunkAudioEnregistreEvent(sessionId, 0, new byte[]{1});
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("texte", List.of()));
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));

        transcriptionService.surChunkEnregistre(evenement);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void surChunkEnregistre_publie_un_evenement_quand_la_session_est_terminee_et_tous_les_chunks_sont_transcrits() {
        UUID sessionId = UUID.randomUUID();
        ChunkAudioEnregistreEvent evenement = new ChunkAudioEnregistreEvent(sessionId, 1, new byte[]{1});
        Session session = new Session("Cours");
        session.terminer();
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("texte", List.of()));
        when(sessionService.obtenirSession(sessionId)).thenReturn(session);
        when(audioChunkRepository.countBySessionId(sessionId)).thenReturn(2L);
        when(transcriptionRepository.countBySessionId(sessionId)).thenReturn(2L);

        transcriptionService.surChunkEnregistre(evenement);

        verify(eventPublisher).publishEvent(new ToutesTranscriptionsTermineesEvent(sessionId));
    }

    @Test
    void surChunkEnregistre_ne_publie_rien_si_des_chunks_restent_a_transcrire() {
        UUID sessionId = UUID.randomUUID();
        ChunkAudioEnregistreEvent evenement = new ChunkAudioEnregistreEvent(sessionId, 0, new byte[]{1});
        Session session = new Session("Cours");
        session.terminer();
        when(transcripteur.transcrire(any())).thenReturn(new ResultatTranscription("texte", List.of()));
        when(sessionService.obtenirSession(sessionId)).thenReturn(session);
        when(audioChunkRepository.countBySessionId(sessionId)).thenReturn(3L);
        when(transcriptionRepository.countBySessionId(sessionId)).thenReturn(2L);

        transcriptionService.surChunkEnregistre(evenement);

        verify(eventPublisher, never()).publishEvent(any());
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

    @Test
    void obtenirTranscriptionsAvecIdentification_resout_le_nom_des_segments_identifies() {
        UUID sessionId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("jean@test.fr", "hash");
        utilisateur.renseignerNom("Jean Dupont");
        UUID utilisateurId = utilisateur.getId(); // l'id genere par le constructeur, pas un id independant
        List<SegmentLocuteur> segments = List.of(
                new SegmentLocuteur(1, "Bonjour", 0, 500).avecIdentification(utilisateurId, 0.9),
                new SegmentLocuteur(2, "Salut", 500, 400)
        );
        Transcription transcription = new Transcription(sessionId, 0, "Bonjour Salut", TranscriptionStatut.REUSSIE, segments);
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(transcription));
        when(utilisateurRepository.findAllById(java.util.Set.of(utilisateurId))).thenReturn(List.of(utilisateur));

        List<TranscriptionResponse> resultat = transcriptionService.obtenirTranscriptionsAvecIdentification(sessionId);

        List<SegmentLocuteurResponse> segmentsResponse = resultat.get(0).segmentsLocuteur();
        assertThat(segmentsResponse.get(0).nomUtilisateurIdentifie()).isEqualTo("Jean Dupont");
        assertThat(segmentsResponse.get(1).nomUtilisateurIdentifie()).isNull();
    }

    @Test
    void obtenirTranscriptionsAvecIdentification_tolere_un_utilisateur_identifie_introuvable() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurSupprimeId = UUID.randomUUID();
        List<SegmentLocuteur> segments = List.of(
                new SegmentLocuteur(1, "Bonjour", 0, 500).avecIdentification(utilisateurSupprimeId, 0.9)
        );
        Transcription transcription = new Transcription(sessionId, 0, "Bonjour", TranscriptionStatut.REUSSIE, segments);
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(transcription));
        when(utilisateurRepository.findAllById(java.util.Set.of(utilisateurSupprimeId))).thenReturn(List.of());

        List<TranscriptionResponse> resultat = transcriptionService.obtenirTranscriptionsAvecIdentification(sessionId);

        assertThat(resultat.get(0).segmentsLocuteur().get(0).nomUtilisateurIdentifie()).isNull();
    }
}
