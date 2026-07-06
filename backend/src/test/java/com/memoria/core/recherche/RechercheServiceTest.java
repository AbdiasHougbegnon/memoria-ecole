package com.memoria.core.recherche;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionStatus;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.SegmentLocuteur;
import com.memoria.core.transcription.ToutesTranscriptionsTermineesEvent;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RechercheServiceTest {

    @Mock
    private IndexRechercheRepository indexRechercheRepository;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private GenerateurEmbeddingPort generateurEmbedding;

    @Mock
    private RecherchePort recherche;

    private RechercheService rechercheService;

    @BeforeEach
    void setUp() {
        rechercheService = new RechercheService(
                indexRechercheRepository, transcriptionRepository, sessionService, generateurEmbedding, recherche
        );
    }

    @Test
    void surSessionTerminee_indexe_les_segments_des_transcriptions_reussies() {
        UUID sessionId = UUID.randomUUID();
        Transcription transcription = new Transcription(
                sessionId, 0, "Bonjour a tous. Nous avons decide X.", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour a tous.", 0, 1500),
                        new SegmentLocuteur(2, "Nous avons decide X.", 1500, 2000))
        );
        when(indexRechercheRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(transcription));
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Reunion"));
        float[] vecteur1 = {0.1f, 0.2f};
        float[] vecteur2 = {0.3f, 0.4f};
        when(generateurEmbedding.genererEmbeddings(List.of("Bonjour a tous.", "Nous avons decide X.")))
                .thenReturn(List.of(vecteur1, vecteur2));

        rechercheService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<List<SegmentARecherche>> segmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(recherche).indexerSegments(any(), any(), any(), segmentsCaptor.capture(), any());
        assertThat(segmentsCaptor.getValue()).hasSize(2);
        assertThat(segmentsCaptor.getValue().get(0).texte()).isEqualTo("Bonjour a tous.");

        ArgumentCaptor<IndexRecherche> indexCaptor = ArgumentCaptor.forClass(IndexRecherche.class);
        verify(indexRechercheRepository).save(indexCaptor.capture());
        assertThat(indexCaptor.getValue().getStatut()).isEqualTo(StatutIndexRecherche.REUSSI);
        assertThat(indexCaptor.getValue().getNombreSegments()).isEqualTo(2);
    }

    @Test
    void surSessionTerminee_ne_fait_rien_si_aucune_transcription_na_reussi() {
        UUID sessionId = UUID.randomUUID();
        Transcription transcription = new Transcription(sessionId, 0, null, TranscriptionStatut.ECHEC);
        when(indexRechercheRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(transcription));

        rechercheService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(generateurEmbedding, never()).genererEmbeddings(anyList());
        verify(indexRechercheRepository, never()).save(any());
    }

    @Test
    void surSessionTerminee_ne_fait_rien_si_les_transcriptions_reussies_nont_aucun_segment_exploitable() {
        UUID sessionId = UUID.randomUUID();
        Transcription transcription = new Transcription(
                sessionId, 0, "  ", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "  ", 0, 100))
        );
        when(indexRechercheRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(transcription));

        rechercheService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(generateurEmbedding, never()).genererEmbeddings(anyList());
        verify(sessionService, never()).obtenirSession(any());
    }

    @Test
    void surSessionTerminee_marque_echec_quand_lembedding_echoue() {
        UUID sessionId = UUID.randomUUID();
        Transcription transcription = new Transcription(
                sessionId, 0, "Bonjour.", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour.", 0, 500))
        );
        when(indexRechercheRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(transcription));
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Reunion"));
        when(generateurEmbedding.genererEmbeddings(anyList()))
                .thenThrow(new GenerationEmbeddingException("Azure OpenAI indisponible"));

        rechercheService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<IndexRecherche> indexCaptor = ArgumentCaptor.forClass(IndexRecherche.class);
        verify(indexRechercheRepository).save(indexCaptor.capture());
        assertThat(indexCaptor.getValue().getStatut()).isEqualTo(StatutIndexRecherche.ECHEC);
        verify(recherche, never()).indexerSegments(any(), any(), any(), any(), any());
    }

    @Test
    void surToutesTranscriptionsTerminees_indexe_si_rien_nexiste_encore() {
        UUID sessionId = UUID.randomUUID();
        Transcription transcription = new Transcription(
                sessionId, 0, "Bonjour.", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour.", 0, 500))
        );
        when(indexRechercheRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of(transcription));
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(generateurEmbedding.genererEmbeddings(anyList())).thenReturn(List.of(new float[]{0.1f}));

        rechercheService.surToutesTranscriptionsTerminees(new ToutesTranscriptionsTermineesEvent(sessionId));

        verify(indexRechercheRepository).save(any());
    }

    @Test
    void indexerSiPossible_ne_fait_rien_si_la_session_est_deja_indexee() {
        UUID sessionId = UUID.randomUUID();
        when(indexRechercheRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(new IndexRecherche(sessionId, 2, StatutIndexRecherche.REUSSI)));

        rechercheService.surSessionTerminee(new SessionTermineeEvent(sessionId));
        rechercheService.surToutesTranscriptionsTerminees(new ToutesTranscriptionsTermineesEvent(sessionId));

        verify(transcriptionRepository, never()).findBySessionIdOrderByNumeroSequenceAsc(any());
        verify(generateurEmbedding, never()).genererEmbeddings(anyList());
    }

    @Test
    void reindexerHistorique_indexe_les_sessions_terminees_pas_encore_indexees_et_ignore_les_autres() {
        Session sessionTermineeAIndexer = new Session("Cours 1");
        sessionTermineeAIndexer.terminer();
        Session sessionTermineeSansTranscriptionReussie = new Session("Cours 2");
        sessionTermineeSansTranscriptionReussie.terminer();
        Session sessionEnCours = new Session("Cours 3");

        when(sessionService.listerSessions()).thenReturn(
                List.of(sessionTermineeAIndexer, sessionTermineeSansTranscriptionReussie, sessionEnCours)
        );
        when(indexRechercheRepository.findBySessionId(sessionTermineeAIndexer.getId())).thenReturn(Optional.empty());
        when(indexRechercheRepository.findBySessionId(sessionTermineeSansTranscriptionReussie.getId())).thenReturn(Optional.empty());

        Transcription transcription = new Transcription(
                sessionTermineeAIndexer.getId(), 0, "Bonjour.", TranscriptionStatut.REUSSIE,
                List.of(new SegmentLocuteur(1, "Bonjour.", 0, 500))
        );
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionTermineeAIndexer.getId()))
                .thenReturn(List.of(transcription));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionTermineeSansTranscriptionReussie.getId()))
                .thenReturn(List.of());
        when(sessionService.obtenirSession(sessionTermineeAIndexer.getId())).thenReturn(sessionTermineeAIndexer);
        when(generateurEmbedding.genererEmbeddings(anyList())).thenReturn(List.of(new float[]{0.1f}));

        rechercheService.reindexerHistorique();

        verify(indexRechercheRepository, times(1)).save(any());
        verify(transcriptionRepository, never()).findBySessionIdOrderByNumeroSequenceAsc(sessionEnCours.getId());
    }

    @Test
    void rechercher_embed_la_requete_et_delegue_au_port_de_recherche() {
        float[] vecteurRequete = {0.5f, 0.6f};
        when(generateurEmbedding.genererEmbeddings(List.of("ou en est le projet ?"))).thenReturn(List.of(vecteurRequete));
        List<ResultatRecherche> resultatsAttendus = List.of(new ResultatRecherche(
                UUID.randomUUID(), "Reunion", java.time.Instant.now(), "Nous avons decide X.", 1, 1500, 2000, 0, 0.9
        ));
        when(recherche.rechercher("ou en est le projet ?", vecteurRequete, 5)).thenReturn(resultatsAttendus);

        List<ResultatRecherche> resultats = rechercheService.rechercher("ou en est le projet ?", 5);

        assertThat(resultats).isEqualTo(resultatsAttendus);
        verify(recherche, times(1)).rechercher("ou en est le projet ?", vecteurRequete, 5);
    }
}
