package com.memoria.core.gouvernance;

import com.memoria.core.auth.ModuleMemoria;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Droit a la portabilite : reprend exactement le meme perimetre que
// GouvernanceDonneesService.effacerCompte (voir docs/phases/phase-13-gouvernance-donnees.md),
// en lecture seule. Le vecteur/profil biometrique externe n'est jamais inclus
// (voir EmpreinteVocaleExportee) -- seule sa presence et sa date de
// consentement le sont.
public record ExportDonneesUtilisateur(
        UUID utilisateurId,
        String email,
        String nom,
        Instant dateCreation,
        ModuleMemoria module,
        List<SessionExportee> sessions,
        List<EngagementExporte> engagementsResponsable,
        EmpreinteVocaleExportee empreinteVocale,
        List<SeanceTutoratExportee> seancesTutorat,
        List<MaitriseNotionExportee> maitrisesNotions,
        List<TentativeQcmExportee> tentativesQcm,
        List<CouloirExporte> couloirs
) {
    public record SessionExportee(
            UUID id,
            String titre,
            Instant dateCreation,
            UUID couloirId,
            String transcriptionComplete,
            List<ResumeExporte> resumes,
            String compteRendu,
            String resumeCours
    ) {
    }

    public record ResumeExporte(String type, String texte) {
    }

    public record EngagementExporte(UUID id, UUID sessionId, String description, String echeance, String statut) {
    }

    public record EmpreinteVocaleExportee(boolean presente, Instant dateConsentement) {
    }

    public record SeanceTutoratExportee(
            UUID id,
            UUID seanceId,
            String statut,
            String mode,
            Instant dateDebut,
            Instant dateFin,
            List<TourExporte> tours
    ) {
    }

    public record TourExporte(String locuteur, String texte, Instant dateCreation) {
    }

    public record MaitriseNotionExportee(UUID notionId, String niveau, int nombreTentatives) {
    }

    public record TentativeQcmExportee(UUID qcmId, int score, int nombreQuestions, int nombreTentatives) {
    }

    public record CouloirExporte(UUID id, String nom, boolean proprietaire) {
    }
}
