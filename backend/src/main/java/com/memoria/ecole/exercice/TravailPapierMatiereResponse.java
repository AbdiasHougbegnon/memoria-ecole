package com.memoria.ecole.exercice;

import com.memoria.core.document.StatutDocument;
import com.memoria.core.document.TypeDocument;
import com.memoria.ecole.notion.NiveauMaitrise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TravailPapierMatiereResponse(
        UUID id,
        UUID matiereId,
        TypeDocument type,
        String nomFichier,
        String texteExtrait,
        NiveauMaitrise correctionNiveau,
        String correctionSynthese,
        List<PointCorrectionResponse> pointsCorrection,
        StatutDocument statut,
        Instant dateCreation
) {
    public static TravailPapierMatiereResponse depuis(TravailPapierMatiere travail) {
        return new TravailPapierMatiereResponse(
                travail.getId(),
                travail.getMatiereId(),
                travail.getType(),
                travail.getNomFichier(),
                travail.getTexteExtrait(),
                travail.getCorrectionNiveau(),
                travail.getCorrectionSynthese(),
                travail.getPointsCorrection().stream().map(PointCorrectionResponse::depuis).toList(),
                travail.getStatut(),
                travail.getDateCreation()
        );
    }

    public record PointCorrectionResponse(String sujet, String constat, String correctionAttendue) {
        public static PointCorrectionResponse depuis(PointCorrection point) {
            return new PointCorrectionResponse(point.getSujet(), point.getConstat(), point.getCorrectionAttendue());
        }
    }
}
