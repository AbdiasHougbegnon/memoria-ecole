package com.memoria.ecole.qcm;

import com.memoria.core.session.SessionNotFoundException;
import com.memoria.core.session.SessionService;
import com.memoria.ecole.resumecours.NotionCours;
import com.memoria.ecole.resumecours.ResumeCours;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.resumecours.StatutResumeCours;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QcmServiceTest {

    @Mock
    private QcmRepository qcmRepository;

    @Mock
    private ResumeCoursRepository resumeCoursRepository;

    @Mock
    private GenerateurQcmPort generateurQcm;

    @Mock
    private SessionService sessionService;

    @Mock
    private TentativeQcmRepository tentativeQcmRepository;

    private QcmService qcmService;

    @BeforeEach
    void setUp() {
        qcmService = new QcmService(
                qcmRepository, resumeCoursRepository, generateurQcm, sessionService, tentativeQcmRepository
        );
    }

    private ResumeCours resumeCoursReussi(UUID sessionId) {
        return new ResumeCours(
                sessionId, "Synthese du cours sur la photosynthese.",
                List.of(new NotionCours("Photosynthese", "Conversion de la lumiere en energie")),
                List.of("Revoir le cycle de Calvin"), List.of(0, 2), StatutResumeCours.REUSSI
        );
    }

    @Test
    void obtenirOuGenererQcm_genere_et_sauvegarde_a_partir_du_resume_de_cours() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        ResumeCours resumeCours = resumeCoursReussi(sessionId);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resumeCours));
        when(generateurQcm.genererQcm(anyString())).thenReturn(new QcmGenere(List.of(
                new QuestionExtraite(
                        "Qu'est-ce que la photosynthese ?",
                        List.of("Un processus chimique", "Un organe", "Une planete", "Un mineral"),
                        0,
                        "La photosynthese convertit la lumiere en energie."
                )
        )));
        when(qcmRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Qcm resultat = qcmService.obtenirOuGenererQcm(sessionId, utilisateurId);

        verify(sessionService).verifierAcces(sessionId, utilisateurId);
        ArgumentCaptor<Qcm> captor = ArgumentCaptor.forClass(Qcm.class);
        verify(qcmRepository).save(captor.capture());
        Qcm qcm = captor.getValue();
        assertThat(qcm.getSessionId()).isEqualTo(sessionId);
        assertThat(qcm.getQuestions()).hasSize(1);
        assertThat(qcm.getQuestions().get(0).getEnonce()).isEqualTo("Qu'est-ce que la photosynthese ?");
        assertThat(qcm.getQuestions().get(0).getChoixA()).isEqualTo("Un processus chimique");
        assertThat(qcm.getQuestions().get(0).getIndexReponseCorrecte()).isEqualTo(0);
        assertThat(qcm.getSegmentsSources()).containsExactly(0, 2);
        assertThat(qcm.getStatut()).isEqualTo(StatutQcm.REUSSI);
        assertThat(resultat).isEqualTo(qcm);
    }

    @Test
    void obtenirOuGenererQcm_marque_echec_quand_le_generateur_echoue() {
        UUID sessionId = UUID.randomUUID();
        ResumeCours resumeCours = resumeCoursReussi(sessionId);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resumeCours));
        when(generateurQcm.genererQcm(anyString())).thenThrow(new GenerationQcmException("Azure OpenAI indisponible"));
        when(qcmRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Qcm resultat = qcmService.obtenirOuGenererQcm(sessionId, UUID.randomUUID());

        assertThat(resultat.getStatut()).isEqualTo(StatutQcm.ECHEC);
        assertThat(resultat.getQuestions()).isEmpty();
        assertThat(resultat.getSegmentsSources()).containsExactly(0, 2);
    }

    @Test
    void obtenirOuGenererQcm_renvoie_le_qcm_deja_en_cache_sans_regenerer() {
        UUID sessionId = UUID.randomUUID();
        Qcm dejaGenere = new Qcm(sessionId, List.of(), List.of(0), StatutQcm.REUSSI);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(dejaGenere));

        Qcm resultat = qcmService.obtenirOuGenererQcm(sessionId, UUID.randomUUID());

        assertThat(resultat).isSameAs(dejaGenere);
        verify(generateurQcm, never()).genererQcm(any());
        verify(resumeCoursRepository, never()).findBySessionId(any());
    }

    @Test
    void obtenirOuGenererQcm_leve_une_exception_si_aucun_resume_de_cours_nexiste() {
        UUID sessionId = UUID.randomUUID();
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qcmService.obtenirOuGenererQcm(sessionId, UUID.randomUUID()))
                .isInstanceOf(AucunResumeCoursDisponibleException.class);
        verify(generateurQcm, never()).genererQcm(any());
    }

    @Test
    void obtenirOuGenererQcm_leve_une_exception_si_le_resume_de_cours_est_en_echec() {
        UUID sessionId = UUID.randomUUID();
        ResumeCours resumeEnEchec = new ResumeCours(sessionId, null, List.of(), List.of(), List.of(0), StatutResumeCours.ECHEC);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.of(resumeEnEchec));

        assertThatThrownBy(() -> qcmService.obtenirOuGenererQcm(sessionId, UUID.randomUUID()))
                .isInstanceOf(AucunResumeCoursDisponibleException.class);
        verify(generateurQcm, never()).genererQcm(any());
    }

    @Test
    void obtenirOuGenererQcm_leve_une_exception_si_la_session_est_introuvable() {
        UUID idInconnu = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        doThrow(new SessionNotFoundException(idInconnu)).when(sessionService).verifierAcces(idInconnu, utilisateurId);

        assertThatThrownBy(() -> qcmService.obtenirOuGenererQcm(idInconnu, utilisateurId))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void obtenirQcm_retourne_le_qcm_existant() {
        UUID sessionId = UUID.randomUUID();
        Qcm qcm = new Qcm(sessionId, List.of(), List.of(0), StatutQcm.REUSSI);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(qcm));

        Qcm resultat = qcmService.obtenirQcm(sessionId);

        assertThat(resultat).isSameAs(qcm);
    }

    @Test
    void obtenirQcm_leve_une_exception_si_aucun_qcm_nexiste() {
        UUID sessionId = UUID.randomUUID();
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qcmService.obtenirQcm(sessionId))
                .isInstanceOf(QcmNotFoundException.class);
    }

    @Test
    void soumettreTentative_calcule_le_score_et_cree_une_tentative() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Qcm qcm = new Qcm(sessionId, List.of(
                new QuestionQcm("Q1", "A", "B", "C", "D", 0, "car..."),
                new QuestionQcm("Q2", "A", "B", "C", "D", 2, "car...")
        ), List.of(0), StatutQcm.REUSSI);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(qcm));
        when(tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId)).thenReturn(Optional.empty());
        when(tentativeQcmRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TentativeQcm resultat = qcmService.soumettreTentative(sessionId, utilisateurId, List.of(0, 1));

        assertThat(resultat.getScore()).isEqualTo(1);
        assertThat(resultat.getNombreQuestions()).isEqualTo(2);
        assertThat(resultat.getNombreTentatives()).isEqualTo(1);
        assertThat(resultat.getReponsesChoisies()).containsExactly(0, 1);
    }

    @Test
    void soumettreTentative_met_a_jour_une_tentative_existante_et_incremente_le_compteur() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Qcm qcm = new Qcm(sessionId, List.of(new QuestionQcm("Q1", "A", "B", "C", "D", 0, "car...")), List.of(0), StatutQcm.REUSSI);
        TentativeQcm tentativeExistante = new TentativeQcm(qcm.getId(), utilisateurId);
        tentativeExistante.enregistrerReponses(List.of(1), 0, 1);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(qcm));
        when(tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId)).thenReturn(Optional.of(tentativeExistante));
        when(tentativeQcmRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TentativeQcm resultat = qcmService.soumettreTentative(sessionId, utilisateurId, List.of(0));

        assertThat(resultat).isSameAs(tentativeExistante);
        assertThat(resultat.getScore()).isEqualTo(1);
        assertThat(resultat.getNombreTentatives()).isEqualTo(2);
    }

    @Test
    void soumettreTentative_leve_une_exception_si_le_qcm_est_introuvable() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qcmService.soumettreTentative(sessionId, utilisateurId, List.of(0)))
                .isInstanceOf(QcmNotFoundException.class);
    }

    @Test
    void obtenirMaTentative_retourne_la_tentative_existante() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Qcm qcm = new Qcm(sessionId, List.of(), List.of(0), StatutQcm.REUSSI);
        TentativeQcm tentative = new TentativeQcm(qcm.getId(), utilisateurId);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(qcm));
        when(tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId)).thenReturn(Optional.of(tentative));

        Optional<TentativeQcm> resultat = qcmService.obtenirMaTentative(sessionId, utilisateurId);

        assertThat(resultat).contains(tentative);
    }

    @Test
    void obtenirMaTentative_retourne_vide_si_aucune_tentative() {
        UUID sessionId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Qcm qcm = new Qcm(sessionId, List.of(), List.of(0), StatutQcm.REUSSI);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(qcm));
        when(tentativeQcmRepository.findByQcmIdAndUtilisateurId(qcm.getId(), utilisateurId)).thenReturn(Optional.empty());

        assertThat(qcmService.obtenirMaTentative(sessionId, utilisateurId)).isEmpty();
    }
}
