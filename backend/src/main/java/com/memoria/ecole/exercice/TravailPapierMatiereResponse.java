package com.memoria.ecole.exercice;

import com.memoria.core.document.StatutDocument;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TravailPapierMatiereResponse(
        UUID id,
        UUID matiereId,
        String nomFichierEnonce,
        String nomFichierReponse,
        StatutDocument statut,
        Instant dateCreation,
        List<ExercicePapierResponse> exercices
) {
    public static TravailPapierMatiereResponse depuis(TravailPapierMatiere travail, List<ExercicePapier> exercices) {
        return new TravailPapierMatiereResponse(
                travail.getId(),
                travail.getMatiereId(),
                travail.getNomFichierEnonce(),
                travail.getNomFichierReponse(),
                travail.getStatut(),
                travail.getDateCreation(),
                exercices.stream().map(ExercicePapierResponse::depuis).toList()
        );
    }

    public record ExercicePapierResponse(
            UUID id, int ordre, String enonce, String reponseEtudiant,
            com.memoria.ecole.notion.NiveauMaitrise correctionNiveau, String correctionSynthese,
            List<TravailPapierMatiereResponse.PointCorrectionResponse> pointsCorrection,
            StatutVerification statutVerification, String questionVerificationEnonce,
            List<ChoixVerificationResponse> choixVerification
    ) {
        public static ExercicePapierResponse depuis(ExercicePapier exercice) {
            return new ExercicePapierResponse(
                    exercice.getId(),
                    exercice.getOrdre(),
                    exercice.getEnonce(),
                    exercice.getReponseEtudiant(),
                    exercice.getCorrectionNiveau(),
                    exercice.getCorrectionSynthese(),
                    exercice.getPointsCorrection().stream().map(PointCorrectionResponse::depuis).toList(),
                    exercice.getStatutVerification(),
                    exercice.getQuestionVerificationEnonce(),
                    exercice.getChoixVerification() == null
                            ? List.of()
                            : exercice.getChoixVerification().stream().map(ChoixVerificationResponse::depuis).toList()
            );
        }
    }

    public record PointCorrectionResponse(String sujet, String constat, String correctionAttendue) {
        public static PointCorrectionResponse depuis(PointCorrection point) {
            return new PointCorrectionResponse(point.getSujet(), point.getConstat(), point.getCorrectionAttendue());
        }
    }

    public record ChoixVerificationResponse(String texte, boolean correct) {
        public static ChoixVerificationResponse depuis(ChoixVerification choix) {
            return new ChoixVerificationResponse(choix.getTexte(), choix.isCorrect());
        }
    }
}
