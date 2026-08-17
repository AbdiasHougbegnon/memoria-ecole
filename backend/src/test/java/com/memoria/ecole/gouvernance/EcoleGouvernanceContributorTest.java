package com.memoria.ecole.gouvernance;

import com.memoria.core.gouvernance.ExportDonneesUtilisateur;
import com.memoria.ecole.notion.MaitriseNotion;
import com.memoria.ecole.notion.MaitriseNotionRepository;
import com.memoria.ecole.qcm.Qcm;
import com.memoria.ecole.qcm.QcmRepository;
import com.memoria.ecole.qcm.StatutQcm;
import com.memoria.ecole.qcm.TentativeQcm;
import com.memoria.ecole.qcm.TentativeQcmRepository;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.tuteurvocal.Locuteur;
import com.memoria.ecole.tuteurvocal.ModeTutorat;
import com.memoria.ecole.tuteurvocal.SeanceTutorat;
import com.memoria.ecole.tuteurvocal.SeanceTutoratRepository;
import com.memoria.ecole.tuteurvocal.TourDialogueTutorat;
import com.memoria.ecole.tuteurvocal.TourDialogueTutoratRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcoleGouvernanceContributorTest {

    @Mock private SeanceTutoratRepository seanceTutoratRepository;
    @Mock private TourDialogueTutoratRepository tourDialogueTutoratRepository;
    @Mock private MaitriseNotionRepository maitriseNotionRepository;
    @Mock private TentativeQcmRepository tentativeQcmRepository;
    @Mock private ResumeCoursRepository resumeCoursRepository;
    @Mock private QcmRepository qcmRepository;

    private EcoleGouvernanceContributor contributor;

    @BeforeEach
    void setUp() {
        contributor = new EcoleGouvernanceContributor(
                seanceTutoratRepository, tourDialogueTutoratRepository, maitriseNotionRepository,
                tentativeQcmRepository, resumeCoursRepository, qcmRepository
        );
    }

    @Test
    void effacerDonneesUtilisateur_supprime_les_seances_de_tutorat_leurs_tours_et_les_maitrises() {
        UUID utilisateurId = UUID.randomUUID();
        SeanceTutorat seance = new SeanceTutorat(UUID.randomUUID(), utilisateurId, null, ModeTutorat.EXPLICATION);
        when(seanceTutoratRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of(seance));

        contributor.effacerDonneesUtilisateur(utilisateurId);

        verify(tourDialogueTutoratRepository).deleteBySeanceTutoratId(seance.getId());
        verify(seanceTutoratRepository).delete(seance);
        verify(maitriseNotionRepository).deleteByUtilisateurId(utilisateurId);
        verify(tentativeQcmRepository).deleteByUtilisateurId(utilisateurId);
    }

    @Test
    void purgerDonneesSession_supprime_le_resume_de_cours_et_le_qcm() {
        UUID sessionId = UUID.randomUUID();
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        contributor.purgerDonneesSession(sessionId);

        verify(resumeCoursRepository).deleteBySessionId(sessionId);
        verify(qcmRepository).deleteBySessionId(sessionId);
    }

    @Test
    void purgerDonneesSession_supprime_les_tentatives_du_qcm_de_la_session() {
        UUID sessionId = UUID.randomUUID();
        Qcm qcm = new Qcm(sessionId, List.of(), List.of(0), StatutQcm.REUSSI);
        when(qcmRepository.findBySessionId(sessionId)).thenReturn(Optional.of(qcm));

        contributor.purgerDonneesSession(sessionId);

        verify(tentativeQcmRepository).deleteByQcmId(qcm.getId());
        verify(qcmRepository).deleteBySessionId(sessionId);
    }

    @Test
    void exporterSeancesTutorat_inclut_lhistorique_des_tours_dans_lordre() {
        UUID utilisateurId = UUID.randomUUID();
        SeanceTutorat seance = new SeanceTutorat(UUID.randomUUID(), utilisateurId, null, ModeTutorat.LIBRE);
        TourDialogueTutorat tour = new TourDialogueTutorat(seance.getId(), null, Locuteur.ETUDIANT, "Bonjour", ModeTutorat.LIBRE);
        when(seanceTutoratRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of(seance));
        when(tourDialogueTutoratRepository.findBySeanceTutoratIdOrderByDateCreationAsc(seance.getId())).thenReturn(List.of(tour));

        List<ExportDonneesUtilisateur.SeanceTutoratExportee> export = contributor.exporterSeancesTutorat(utilisateurId);

        assertThat(export).hasSize(1);
        assertThat(export.get(0).mode()).isEqualTo("LIBRE");
        assertThat(export.get(0).tours()).hasSize(1);
        assertThat(export.get(0).tours().get(0).texte()).isEqualTo("Bonjour");
    }

    @Test
    void exporterMaitrises_projette_le_niveau_et_le_nombre_de_tentatives() {
        UUID utilisateurId = UUID.randomUUID();
        MaitriseNotion maitrise = new MaitriseNotion(UUID.randomUUID(), utilisateurId);
        when(maitriseNotionRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of(maitrise));

        List<ExportDonneesUtilisateur.MaitriseNotionExportee> export = contributor.exporterMaitrises(utilisateurId);

        assertThat(export).hasSize(1);
        assertThat(export.get(0).niveau()).isEqualTo("NON_ABORDEE");
    }

    @Test
    void exporterTentativesQcm_projette_le_score_et_le_nombre_de_questions() {
        UUID utilisateurId = UUID.randomUUID();
        TentativeQcm tentative = new TentativeQcm(UUID.randomUUID(), utilisateurId);
        when(tentativeQcmRepository.findByUtilisateurId(utilisateurId)).thenReturn(List.of(tentative));

        List<ExportDonneesUtilisateur.TentativeQcmExportee> export = contributor.exporterTentativesQcm(utilisateurId);

        assertThat(export).hasSize(1);
        assertThat(export.get(0).score()).isZero();
    }

    @Test
    void exporterResumeCoursSession_absent_si_aucun_resume_de_cours() {
        UUID sessionId = UUID.randomUUID();
        when(resumeCoursRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThat(contributor.exporterResumeCoursSession(sessionId)).isEmpty();
    }
}
