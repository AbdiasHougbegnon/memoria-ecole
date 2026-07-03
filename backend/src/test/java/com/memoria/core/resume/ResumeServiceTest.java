package com.memoria.core.resume;

import com.memoria.core.session.SessionTermineeEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private GenerateurResumePort generateurResume;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(resumeRepository, transcriptionRepository, generateurResume);
    }

    @Test
    void surSessionTerminee_genere_et_sauvegarde_le_resume_a_partir_des_transcriptions_reussies() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour a tous.", TranscriptionStatut.REUSSIE),
                new Transcription(sessionId, 1, null, TranscriptionStatut.ECHEC),
                new Transcription(sessionId, 2, "Nous avons decide X.", TranscriptionStatut.REUSSIE)
        );
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResume.genererResume("Bonjour a tous. Nous avons decide X."))
                .thenReturn(new ResumeGenere("Synthese de la reunion.", List.of("Decision X prise")));

        resumeService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Resume resume = captor.getValue();
        assertThat(resume.getSessionId()).isEqualTo(sessionId);
        assertThat(resume.getTexteResume()).isEqualTo("Synthese de la reunion.");
        assertThat(resume.getPointsCles()).containsExactly("Decision X prise");
        assertThat(resume.getSegmentsSources()).containsExactly(0, 2);
        assertThat(resume.getStatut()).isEqualTo(ResumeStatut.REUSSI);
    }

    @Test
    void surSessionTerminee_ne_genere_rien_si_aucune_transcription_na_reussi() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, null, TranscriptionStatut.ECHEC)
        );
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);

        resumeService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(generateurResume, never()).genererResume(any());
        verify(resumeRepository, never()).save(any());
    }

    @Test
    void surSessionTerminee_marque_echec_quand_le_generateur_echoue() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour.", TranscriptionStatut.REUSSIE)
        );
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResume.genererResume(any())).thenThrow(new GenerationResumeException("Azure OpenAI indisponible"));

        resumeService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Resume resume = captor.getValue();
        assertThat(resume.getStatut()).isEqualTo(ResumeStatut.ECHEC);
        assertThat(resume.getTexteResume()).isNull();
        assertThat(resume.getSegmentsSources()).containsExactly(0);
    }

    @Test
    void obtenirResume_retourne_le_resume_existant() {
        UUID sessionId = UUID.randomUUID();
        Resume resume = new Resume(sessionId, "Texte", List.of("Point"), List.of(0), ResumeStatut.REUSSI);
        when(resumeRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resume));

        Resume resultat = resumeService.obtenirResume(sessionId);

        assertThat(resultat).isSameAs(resume);
    }

    @Test
    void obtenirResume_leve_une_exception_si_aucun_resume_nexiste() {
        UUID sessionId = UUID.randomUUID();
        when(resumeRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.obtenirResume(sessionId))
                .isInstanceOf(ResumeNotFoundException.class);
    }
}
