package com.memoria.core.filmemoire;

import com.memoria.core.recherche.GenerateurEmbeddingPort;
import com.memoria.core.resume.AucuneTranscriptionDisponibleException;
import com.memoria.core.resume.Resume;
import com.memoria.core.resume.ResumeService;
import com.memoria.core.resume.ResumeStatut;
import com.memoria.core.resume.ResumeType;
import com.memoria.core.session.SessionTermineeEvent;
import com.memoria.core.transcription.ToutesTranscriptionsTermineesEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilMemoireServiceTest {

    @Mock
    private FilMemoireRepository filMemoireRepository;

    @Mock
    private ResumeService resumeService;

    @Mock
    private GenerateurEmbeddingPort generateurEmbedding;

    @Mock
    private GenerateurFilMemoirePort generateurFilMemoire;

    private FilMemoireService filMemoireService;

    @BeforeEach
    void setUp() {
        filMemoireService = new FilMemoireService(
                filMemoireRepository, resumeService, generateurEmbedding, generateurFilMemoire
        );
    }

    private Resume resumeReussi(UUID sessionId, String texte) {
        return new Resume(sessionId, ResumeType.DETAILLE, texte, List.of(), List.of(0), ResumeStatut.REUSSI);
    }

    @Test
    void surSessionTerminee_cree_un_nouveau_fil_quand_aucun_fil_nexiste() {
        UUID sessionId = UUID.randomUUID();
        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE))
                .thenReturn(resumeReussi(sessionId, "Reunion sur le lancement du produit X."));
        when(filMemoireRepository.findAll()).thenReturn(List.of());
        when(generateurEmbedding.genererEmbeddings(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(generateurFilMemoire.deciderFil("Reunion sur le lancement du produit X.", List.of()))
                .thenReturn(new DecisionFilMemoire(null, "Lancement produit X", "Reunion sur le lancement du produit X."));

        filMemoireService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<FilMemoire> captor = ArgumentCaptor.forClass(FilMemoire.class);
        verify(filMemoireRepository).save(captor.capture());
        FilMemoire fil = captor.getValue();
        assertThat(fil.getNom()).isEqualTo("Lancement produit X");
        assertThat(fil.getResumeCumulatif()).isEqualTo("Reunion sur le lancement du produit X.");
        assertThat(fil.getSessionIds()).containsExactly(sessionId);
    }

    @Test
    void surSessionTerminee_rejoint_un_fil_existant_quand_la_decision_le_designe() {
        UUID sessionId = UUID.randomUUID();
        FilMemoire filExistant = new FilMemoire(
                "Lancement produit X", "Premiere reunion sur le produit X.",
                VecteurUtils.versOctets(new float[]{0.1f, 0.2f}), new java.util.ArrayList<>(List.of(UUID.randomUUID()))
        );
        UUID filExistantId = filExistant.getId();

        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE))
                .thenReturn(resumeReussi(sessionId, "Suite de la reunion sur le produit X."));
        when(filMemoireRepository.findAll()).thenReturn(List.of(filExistant));
        when(generateurEmbedding.genererEmbeddings(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(generateurFilMemoire.deciderFil(any(), anyList()))
                .thenReturn(new DecisionFilMemoire(filExistantId, null, "Resume cumulatif mis a jour."));

        filMemoireService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(filMemoireRepository).save(filExistant);
        assertThat(filExistant.getResumeCumulatif()).isEqualTo("Resume cumulatif mis a jour.");
        assertThat(filExistant.getSessionIds()).contains(sessionId);
    }

    @Test
    void surSessionTerminee_ne_fait_rien_si_deja_associee_a_un_fil() {
        UUID sessionId = UUID.randomUUID();
        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(true);

        filMemoireService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(resumeService, never()).obtenirOuGenererResume(any(), any());
        verify(generateurEmbedding, never()).genererEmbeddings(anyList());
    }

    @Test
    void surSessionTerminee_ne_fait_rien_si_aucune_transcription_disponible() {
        UUID sessionId = UUID.randomUUID();
        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE))
                .thenThrow(new AucuneTranscriptionDisponibleException(sessionId));

        filMemoireService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(generateurEmbedding, never()).genererEmbeddings(anyList());
        verify(filMemoireRepository, never()).save(any());
    }

    @Test
    void surSessionTerminee_ne_fait_rien_si_le_resume_a_echoue() {
        UUID sessionId = UUID.randomUUID();
        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE))
                .thenReturn(new Resume(sessionId, ResumeType.DETAILLE, null, List.of(), List.of(0), ResumeStatut.ECHEC));

        filMemoireService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(generateurEmbedding, never()).genererEmbeddings(anyList());
    }

    @Test
    void surSessionTerminee_nechoue_pas_et_ne_sauvegarde_rien_si_le_generateur_de_decision_echoue() {
        UUID sessionId = UUID.randomUUID();
        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE))
                .thenReturn(resumeReussi(sessionId, "Un resume."));
        when(generateurEmbedding.genererEmbeddings(anyList()))
                .thenThrow(new RuntimeException("Azure OpenAI indisponible"));

        filMemoireService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        verify(filMemoireRepository, never()).save(any());
    }

    @Test
    void surToutesTranscriptionsTerminees_fonctionne_aussi_seul() {
        UUID sessionId = UUID.randomUUID();
        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE))
                .thenReturn(resumeReussi(sessionId, "Un cours."));
        when(filMemoireRepository.findAll()).thenReturn(List.of());
        when(generateurEmbedding.genererEmbeddings(anyList())).thenReturn(List.of(new float[]{0.5f}));
        when(generateurFilMemoire.deciderFil(any(), anyList()))
                .thenReturn(new DecisionFilMemoire(null, "Nom du fil", "Un cours."));

        filMemoireService.surToutesTranscriptionsTerminees(new ToutesTranscriptionsTermineesEvent(sessionId));

        verify(filMemoireRepository).save(any());
    }

    @Test
    void surSessionTerminee_ne_transmet_que_les_3_fils_les_plus_proches_au_generateur_de_decision() {
        UUID sessionId = UUID.randomUUID();
        FilMemoire filLePlusProche = new FilMemoire("Proche 1", "...", VecteurUtils.versOctets(new float[]{1f, 0f}), new java.util.ArrayList<>());
        FilMemoire filProche2 = new FilMemoire("Proche 2", "...", VecteurUtils.versOctets(new float[]{0.9f, 0.1f}), new java.util.ArrayList<>());
        FilMemoire filProche3 = new FilMemoire("Proche 3", "...", VecteurUtils.versOctets(new float[]{0.7f, 0.3f}), new java.util.ArrayList<>());
        FilMemoire filLointain = new FilMemoire("Lointain", "...", VecteurUtils.versOctets(new float[]{0f, 1f}), new java.util.ArrayList<>());

        when(filMemoireRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(resumeService.obtenirOuGenererResume(sessionId, ResumeType.DETAILLE))
                .thenReturn(resumeReussi(sessionId, "Un resume."));
        when(filMemoireRepository.findAll()).thenReturn(List.of(filLointain, filProche3, filLePlusProche, filProche2));
        when(generateurEmbedding.genererEmbeddings(anyList())).thenReturn(List.of(new float[]{1f, 0f}));
        when(generateurFilMemoire.deciderFil(any(), anyList()))
                .thenReturn(new DecisionFilMemoire(null, "Nouveau", "..."));

        filMemoireService.surSessionTerminee(new SessionTermineeEvent(sessionId));

        ArgumentCaptor<List<CandidatFilMemoire>> captor = ArgumentCaptor.forClass(List.class);
        verify(generateurFilMemoire).deciderFil(any(), captor.capture());
        List<String> nomsCandidats = captor.getValue().stream().map(CandidatFilMemoire::nom).toList();
        assertThat(nomsCandidats).containsExactlyInAnyOrder("Proche 1", "Proche 2", "Proche 3");
        assertThat(nomsCandidats).doesNotContain("Lointain");
    }
}
