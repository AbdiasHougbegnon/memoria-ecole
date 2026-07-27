package com.memoria.ecole.gouvernance;

import com.memoria.core.gouvernance.EffaceurDonneesUtilisateurPort;
import com.memoria.core.gouvernance.ExportDonneesUtilisateur;
import com.memoria.core.gouvernance.ExportateurDonneesUtilisateurPort;
import com.memoria.core.gouvernance.PurgeurDonneesSessionPort;
import com.memoria.ecole.notion.MaitriseNotion;
import com.memoria.ecole.notion.MaitriseNotionRepository;
import com.memoria.ecole.qcm.QcmRepository;
import com.memoria.ecole.qcm.TentativeQcm;
import com.memoria.ecole.qcm.TentativeQcmRepository;
import com.memoria.ecole.resumecours.ResumeCours;
import com.memoria.ecole.resumecours.ResumeCoursRepository;
import com.memoria.ecole.tuteurvocal.SeanceTutorat;
import com.memoria.ecole.tuteurvocal.SeanceTutoratRepository;
import com.memoria.ecole.tuteurvocal.TourDialogueTutorat;
import com.memoria.ecole.tuteurvocal.TourDialogueTutoratRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Part Ecole de la gouvernance des donnees (effacement, export, purge de
// session) -- implemente les ports core.gouvernance.* pour que le moteur
// n'ait jamais a importer de type Ecole (voir audit du 2026-07-27,
// docs/phases/phase-13-gouvernance-donnees.md). Miroir exact de la logique
// qui vivait avant dans GouvernanceDonneesService/SessionPurgeService.
@Component
public class EcoleGouvernanceContributor
        implements EffaceurDonneesUtilisateurPort, ExportateurDonneesUtilisateurPort, PurgeurDonneesSessionPort {

    private final SeanceTutoratRepository seanceTutoratRepository;
    private final TourDialogueTutoratRepository tourDialogueTutoratRepository;
    private final MaitriseNotionRepository maitriseNotionRepository;
    private final TentativeQcmRepository tentativeQcmRepository;
    private final ResumeCoursRepository resumeCoursRepository;
    private final QcmRepository qcmRepository;

    public EcoleGouvernanceContributor(
            SeanceTutoratRepository seanceTutoratRepository,
            TourDialogueTutoratRepository tourDialogueTutoratRepository,
            MaitriseNotionRepository maitriseNotionRepository,
            TentativeQcmRepository tentativeQcmRepository,
            ResumeCoursRepository resumeCoursRepository,
            QcmRepository qcmRepository
    ) {
        this.seanceTutoratRepository = seanceTutoratRepository;
        this.tourDialogueTutoratRepository = tourDialogueTutoratRepository;
        this.maitriseNotionRepository = maitriseNotionRepository;
        this.tentativeQcmRepository = tentativeQcmRepository;
        this.resumeCoursRepository = resumeCoursRepository;
        this.qcmRepository = qcmRepository;
    }

    @Override
    public void effacerDonneesUtilisateur(UUID utilisateurId) {
        for (SeanceTutorat seance : seanceTutoratRepository.findByUtilisateurId(utilisateurId)) {
            tourDialogueTutoratRepository.deleteBySeanceTutoratId(seance.getId());
            seanceTutoratRepository.delete(seance);
        }
        maitriseNotionRepository.deleteByUtilisateurId(utilisateurId);
        tentativeQcmRepository.deleteByUtilisateurId(utilisateurId);
    }

    @Override
    public void purgerDonneesSession(UUID sessionId) {
        resumeCoursRepository.deleteBySessionId(sessionId);
        qcmRepository.findBySessionId(sessionId).ifPresent(qcm -> tentativeQcmRepository.deleteByQcmId(qcm.getId()));
        qcmRepository.deleteBySessionId(sessionId);
    }

    @Override
    public List<ExportDonneesUtilisateur.SeanceTutoratExportee> exporterSeancesTutorat(UUID utilisateurId) {
        return seanceTutoratRepository.findByUtilisateurId(utilisateurId).stream()
                .map(this::exporterSeanceTutorat)
                .toList();
    }

    @Override
    public List<ExportDonneesUtilisateur.MaitriseNotionExportee> exporterMaitrises(UUID utilisateurId) {
        return maitriseNotionRepository.findByUtilisateurId(utilisateurId).stream()
                .map((MaitriseNotion m) -> new ExportDonneesUtilisateur.MaitriseNotionExportee(
                        m.getNotionId(), m.getNiveau().name(), m.getNombreTentatives()))
                .toList();
    }

    @Override
    public List<ExportDonneesUtilisateur.TentativeQcmExportee> exporterTentativesQcm(UUID utilisateurId) {
        return tentativeQcmRepository.findByUtilisateurId(utilisateurId).stream()
                .map((TentativeQcm t) -> new ExportDonneesUtilisateur.TentativeQcmExportee(
                        t.getQcmId(), t.getScore(), t.getNombreQuestions(), t.getNombreTentatives()))
                .toList();
    }

    @Override
    public Optional<String> exporterResumeCoursSession(UUID sessionId) {
        return resumeCoursRepository.findBySessionId(sessionId).map(ResumeCours::getSynthese);
    }

    private ExportDonneesUtilisateur.SeanceTutoratExportee exporterSeanceTutorat(SeanceTutorat seance) {
        List<ExportDonneesUtilisateur.TourExporte> tours = tourDialogueTutoratRepository
                .findBySeanceTutoratIdOrderByDateCreationAsc(seance.getId()).stream()
                .map((TourDialogueTutorat t) -> new ExportDonneesUtilisateur.TourExporte(
                        t.getLocuteur().name(), t.getTexte(), t.getDateCreation()))
                .toList();
        // getMode() peut etre null tant que le backfill manuel de la colonne
        // "mode" (voir docs/phases/phase-19-mode-conversation-libre.md) n'a
        // pas ete execute sur les lignes anterieures a cet increment.
        String mode = seance.getMode() != null ? seance.getMode().name() : null;
        return new ExportDonneesUtilisateur.SeanceTutoratExportee(
                seance.getId(), seance.getSeanceId(), seance.getStatut().name(), mode,
                seance.getDateDebut(), seance.getDateFin(), tours
        );
    }
}
