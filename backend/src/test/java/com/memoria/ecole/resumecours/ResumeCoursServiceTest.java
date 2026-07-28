package com.memoria.ecole.resumecours;

import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeCoursServiceTest {

    @Mock
    private ResumeCoursRepository resumeCoursRepository;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private GenerateurResumeCoursPort generateurResumeCours;

    @Mock
    private SessionService sessionService;

    private ResumeCoursService resumeCoursService;

    @BeforeEach
    void setUp() {
        resumeCoursService = new ResumeCoursService(
                resumeCoursRepository, transcriptionRepository, generateurResumeCours, sessionService
        );
    }

    @Test
    void obtenirOuGenererResumeCours_genere_et_sauvegarde_a_partir_des_transcriptions_reussies() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour a tous.", TranscriptionStatut.REUSSIE),
                new Transcription(sessionId, 1, null, TranscriptionStatut.ECHEC),
                new Transcription(sessionId, 2, "La photosynthese convertit la lumiere.", TranscriptionStatut.REUSSIE)
        );
        UUID utilisateurId = UUID.randomUUID();
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResumeCours.genererResumeCours("Bonjour a tous. La photosynthese convertit la lumiere."))
                .thenReturn(new ResumeCoursGenere(
                        "Synthese du cours.",
                        List.of(new NotionExtraite("Photosynthese", "Processus de conversion de la lumiere en energie")),
                        List.of("Revoir le cycle de Calvin")
                ));
        when(resumeCoursRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeCours resultat = resumeCoursService.obtenirOuGenererResumeCours(sessionId, utilisateurId);

        verify(sessionService).verifierAcces(sessionId, utilisateurId);
        ArgumentCaptor<ResumeCours> captor = ArgumentCaptor.forClass(ResumeCours.class);
        verify(resumeCoursRepository).save(captor.capture());
        ResumeCours resumeCours = captor.getValue();
        assertThat(resumeCours.getSessionId()).isEqualTo(sessionId);
        assertThat(resumeCours.getSynthese()).isEqualTo("Synthese du cours.");
        assertThat(resumeCours.getNotions()).hasSize(1);
        assertThat(resumeCours.getNotions().get(0).getTerme()).isEqualTo("Photosynthese");
        assertThat(resumeCours.getNotions().get(0).getDefinition()).isEqualTo("Processus de conversion de la lumiere en energie");
        assertThat(resumeCours.getPointsARevoir()).containsExactly("Revoir le cycle de Calvin");
        assertThat(resumeCours.getSegmentsSources()).containsExactly(0, 2);
        assertThat(resumeCours.getStatut()).isEqualTo(StatutResumeCours.REUSSI);
        assertThat(resultat).isEqualTo(resumeCours);
    }

    @Test
    void obtenirOuGenererResumeCours_marque_echec_quand_le_generateur_echoue() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour.", TranscriptionStatut.REUSSIE)
        );
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResumeCours.genererResumeCours(any()))
                .thenThrow(new GenerationResumeCoursException("Azure OpenAI indisponible"));
        when(resumeCoursRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResumeCours resultat = resumeCoursService.obtenirOuGenererResumeCours(sessionId, UUID.randomUUID());

        assertThat(resultat.getStatut()).isEqualTo(StatutResumeCours.ECHEC);
        assertThat(resultat.getSynthese()).isNull();
        assertThat(resultat.getNotions()).isEmpty();
        assertThat(resultat.getPointsARevoir()).isEmpty();
        assertThat(resultat.getSegmentsSources()).containsExactly(0);
    }

    @Test
    void obtenirOuGenererResumeCours_renvoie_le_resume_deja_en_cache_sans_regenerer() {
        UUID sessionId = UUID.randomUUID();
        ResumeCours dejaGenere = new ResumeCours(
                sessionId, "Deja fait", List.of(), List.of(), List.of(0), StatutResumeCours.REUSSI
        );
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(dejaGenere));

        ResumeCours resultat = resumeCoursService.obtenirOuGenererResumeCours(sessionId, UUID.randomUUID());

        assertThat(resultat).isSameAs(dejaGenere);
        verify(generateurResumeCours, never()).genererResumeCours(any());
        verify(transcriptionRepository, never()).findBySessionIdOrderByNumeroSequenceAsc(any());
    }

    @Test
    void obtenirOuGenererResumeCours_leve_une_exception_si_aucune_transcription_na_reussi() {
        UUID sessionId = UUID.randomUUID();
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of());

        assertThatThrownBy(() -> resumeCoursService.obtenirOuGenererResumeCours(sessionId, UUID.randomUUID()))
                .isInstanceOf(AucuneTranscriptionDisponibleException.class);
        verify(generateurResumeCours, never()).genererResumeCours(any());
    }

    @Test
    void obtenirOuGenererResumeCours_leve_une_exception_si_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new SessionNotFoundException(idInconnu)).when(sessionService).verifierAcces(idInconnu, utilisateurId);

        assertThatThrownBy(() -> resumeCoursService.obtenirOuGenererResumeCours(idInconnu, utilisateurId))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void obtenirResumeCours_retourne_le_resume_existant() {
        UUID sessionId = UUID.randomUUID();
        ResumeCours resumeCours = new ResumeCours(
                sessionId, "Texte", List.of(), List.of("Point"), List.of(0), StatutResumeCours.REUSSI
        );
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resumeCours));

        ResumeCours resultat = resumeCoursService.obtenirResumeCours(sessionId);

        assertThat(resultat).isSameAs(resumeCours);
    }

    @Test
    void obtenirResumeCours_leve_une_exception_si_aucun_resume_nexiste() {
        UUID sessionId = UUID.randomUUID();
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeCoursService.obtenirResumeCours(sessionId))
                .isInstanceOf(ResumeCoursNotFoundException.class);
    }

    @Test
    void genererFichierTexte_verifie_lacces_et_inclut_synthese_notions_et_points_a_revoir() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        ResumeCours resumeCours = new ResumeCours(
                sessionId, "Synthese du cours.",
                List.of(new NotionCours("Photosynthese", "Conversion de la lumiere en energie")),
                List.of("Revoir le cycle de Calvin"),
                List.of(0), StatutResumeCours.REUSSI
        );
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resumeCours));

        String fichier = resumeCoursService.genererFichierTexte(sessionId, utilisateurId);

        verify(sessionService).verifierAcces(sessionId, utilisateurId);
        assertThat(fichier).contains("Synthese du cours.");
        assertThat(fichier).contains("Photosynthese : Conversion de la lumiere en energie");
        assertThat(fichier).contains("Revoir le cycle de Calvin");
    }

    @Test
    void genererFichierTexte_leve_une_exception_si_aucun_resume_reussi_nexiste() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeCoursService.genererFichierTexte(sessionId, utilisateurId))
                .isInstanceOf(ResumeCoursNotFoundException.class);
    }

    @Test
    void genererFichierTexte_leve_une_exception_si_le_resume_est_en_echec() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        ResumeCours enEchec = new ResumeCours(sessionId, null, List.of(), List.of(), List.of(), StatutResumeCours.ECHEC);
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(enEchec));

        assertThatThrownBy(() -> resumeCoursService.genererFichierTexte(sessionId, utilisateurId))
                .isInstanceOf(ResumeCoursNotFoundException.class);
    }
}
