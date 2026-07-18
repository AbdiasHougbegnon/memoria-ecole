package com.memoria.ecole.resumecours;

import java.time.Instant;
import java.util.List;

public record ResumeCoursResponse(
        String synthese,
        List<NotionCoursResponse> notions,
        List<String> pointsARevoir,
        List<Integer> segmentsSources,
        StatutResumeCours statut,
        Instant dateCreation
) {

    public static ResumeCoursResponse depuis(ResumeCours resumeCours) {
        return new ResumeCoursResponse(
                resumeCours.getSynthese(),
                resumeCours.getNotions().stream().map(NotionCoursResponse::depuis).toList(),
                resumeCours.getPointsARevoir(),
                resumeCours.getSegmentsSources(),
                resumeCours.getStatut(),
                resumeCours.getDateCreation()
        );
    }
}
