package com.memoria.core.resume;

import com.memoria.core.session.Session;
import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.Transcription;
import com.memoria.core.transcription.TranscriptionRepository;
import com.memoria.core.transcription.TranscriptionStatut;
import com.memoria.core.transcription.ToutesTranscriptionsTermineesEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Mock
    private SessionService sessionService;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(resumeRepository, transcriptionRepository, generateurResume, sessionService);
    }

    @Test
    void surSessionTerminee_genere_et_sauvegarde_le_resume_detaille_a_partir_des_transcriptions_reussies() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour a tous.", TranscriptionStatut.REUSSIE),
                new Transcription(sessionId, 1, null, TranscriptionStatut.ECHEC),
                new Transcription(sessionId, 2, "Nous avons decide X.", TranscriptionStatut.REUSSIE)
        );
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResume.genererResume(ResumeType.DETAILLE, "Bonjour a tous. Nous avons decide X."))
                .thenReturn(new ResumeGenere("Synthese de la reunion.", List.of("Decision X prise")));
        when(resumeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        resumeService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Resume resume = captor.getValue();
        assertThat(resume.getSessionId()).isEqualTo(sessionId);
        assertThat(resume.getType()).isEqualTo(ResumeType.DETAILLE);
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
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);

        resumeService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(generateurResume, never()).genererResume(any(), any());
        verify(resumeRepository, never()).save(any());
    }

    @Test
    void surSessionTerminee_marque_echec_quand_le_generateur_echoue() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour.", TranscriptionStatut.REUSSIE)
        );
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResume.genererResume(any(), any())).thenThrow(new GenerationResumeException("Azure OpenAI indisponible"));
        when(resumeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        resumeService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeRepository).save(captor.capture());
        Resume resume = captor.getValue();
        assertThat(resume.getStatut()).isEqualTo(ResumeStatut.ECHEC);
        assertThat(resume.getTexteResume()).isNull();
        assertThat(resume.getSegmentsSources()).containsExactly(0);
    }

    @Test
    void surToutesTranscriptionsTerminees_genere_le_resume_si_aucun_nexiste_encore() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour a tous.", TranscriptionStatut.REUSSIE)
        );
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResume.genererResume(ResumeType.DETAILLE, "Bonjour a tous."))
                .thenReturn(new ResumeGenere("Synthese.", List.of()));
        when(resumeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        resumeService.surToutesTranscriptionsTerminees(new ToutesTranscriptionsTermineesEvent(sessionId));

        verify(resumeRepository).save(any());
    }

    @Test
    void genererResumeDetailleSiPossible_ne_fait_rien_si_un_resume_existe_deja() {
        // Couvre la course entre SessionTermineeEvent et
        // ToutesTranscriptionsTermineesEvent : si l'un des deux a deja
        // genere le resume, l'autre ne doit rien refaire.
        UUID sessionId = UUID.randomUUID();
        Resume resumeExistant = new Resume(sessionId, ResumeType.DETAILLE, "Deja fait", List.of(), List.of(0), ResumeStatut.REUSSI);
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE)).thenReturn(Optional.of(resumeExistant));

        resumeService.surSessionTerminee(new SessionTermineeEvent(sessionId));
        resumeService.surToutesTranscriptionsTerminees(new ToutesTranscriptionsTermineesEvent(sessionId));

        verify(generateurResume, never()).genererResume(any(), any());
        verify(resumeRepository, never()).save(any());
        verify(transcriptionRepository, never()).findBySessionIdOrderByNumeroSequenceAsc(any());
    }

    @Test
    void obtenirOuGenererResume_genere_un_type_a_la_demande_quand_absent() {
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour a tous.", TranscriptionStatut.REUSSIE)
        );
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.ACTIONS)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResume.genererResume(ResumeType.ACTIONS, "Bonjour a tous."))
                .thenReturn(new ResumeGenere("Contexte.", List.of("Envoyer le compte-rendu")));
        when(resumeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Resume resultat = resumeService.obtenirOuGenererResume(sessionId, ResumeType.ACTIONS);

        assertThat(resultat.getType()).isEqualTo(ResumeType.ACTIONS);
        assertThat(resultat.getPointsCles()).containsExactly("Envoyer le compte-rendu");
    }

    @Test
    void obtenirOuGenererResume_renvoie_le_resume_deja_en_cache_sans_regenerer() {
        UUID sessionId = UUID.randomUUID();
        Resume dejaGenere = new Resume(sessionId, ResumeType.COURT, "Court.", List.of(), List.of(0), ResumeStatut.REUSSI);
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.COURT)).thenReturn(Optional.of(dejaGenere));

        Resume resultat = resumeService.obtenirOuGenererResume(sessionId, ResumeType.COURT);

        assertThat(resultat).isSameAs(dejaGenere);
        verify(generateurResume, never()).genererResume(any(), any());
        verify(transcriptionRepository, never()).findBySessionIdOrderByNumeroSequenceAsc(any());
    }

    @Test
    void obtenirOuGenererResume_leve_une_exception_si_aucune_transcription_na_reussi() {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.COURT)).thenReturn(Optional.empty());
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(List.of());

        assertThatThrownBy(() -> resumeService.obtenirOuGenererResume(sessionId, ResumeType.COURT))
                .isInstanceOf(AucuneTranscriptionDisponibleException.class);
        verify(generateurResume, never()).genererResume(any(), any());
    }

    @Test
    void obtenirOuGenererResume_leve_une_exception_si_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        when(sessionService.obtenirSession(idInconnu)).thenThrow(new SessionNotFoundException(idInconnu));

        assertThatThrownBy(() -> resumeService.obtenirOuGenererResume(idInconnu, ResumeType.COURT))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void obtenirOuGenererResume_renvoie_le_resume_du_gagnant_si_une_execution_concurrente_sauvegarde_en_premier() {
        // Fenetre de course : deux executions passent toutes les deux la
        // verification "findBySessionIdAndType absent", mais la contrainte
        // unique (session_id, type) ne laisse qu'une seule sauvegarde
        // reussir. Celle-ci simule le "perdant" : son save() echoue, il doit
        // renvoyer le resultat du "gagnant" au lieu de laisser
        // l'exception se propager.
        UUID sessionId = UUID.randomUUID();
        List<Transcription> transcriptions = List.of(
                new Transcription(sessionId, 0, "Bonjour a tous.", TranscriptionStatut.REUSSIE)
        );
        Resume resumeDuGagnant = new Resume(sessionId, ResumeType.DETAILLE, "Deja sauvegarde par l'autre execution.", List.of(), List.of(0), ResumeStatut.REUSSI);
        when(sessionService.obtenirSession(sessionId)).thenReturn(new Session("Cours"));
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(resumeDuGagnant));
        when(transcriptionRepository.findBySessionIdOrderByNumeroSequenceAsc(sessionId)).thenReturn(transcriptions);
        when(generateurResume.genererResume(ResumeType.DETAILLE, "Bonjour a tous."))
                .thenReturn(new ResumeGenere("Synthese.", List.of()));
        when(resumeRepository.save(any())).thenThrow(new DataIntegrityViolationException("contrainte unique violee"));

        Resume resultat = resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE);

        assertThat(resultat).isSameAs(resumeDuGagnant);
    }

    @Test
    void obtenirResume_retourne_le_resume_existant() {
        UUID sessionId = UUID.randomUUID();
        Resume resume = new Resume(sessionId, ResumeType.DETAILLE, "Texte", List.of("Point"), List.of(0), ResumeStatut.REUSSI);
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE)).thenReturn(Optional.of(resume));

        Resume resultat = resumeService.obtenirResume(sessionId, ResumeType.DETAILLE);

        assertThat(resultat).isSameAs(resume);
    }

    @Test
    void obtenirResume_leve_une_exception_si_aucun_resume_nexiste() {
        UUID sessionId = UUID.randomUUID();
        when(resumeRepository.findBySessionIdAndType(sessionId, ResumeType.DETAILLE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.obtenirResume(sessionId, ResumeType.DETAILLE))
                .isInstanceOf(ResumeNotFoundException.class);
    }
}
