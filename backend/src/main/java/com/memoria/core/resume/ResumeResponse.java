package com.memoria.core.resume;

import java.time.Instant;
import java.util.List;

public record ResumeResponse(
        ResumeType type,
        String texteResume,
        List<String> pointsCles,
        List<Integer> segmentsSources,
        ResumeStatut statut,
        Instant dateCreation
) {

    public static ResumeResponse depuis(Resume resume) {
        return new ResumeResponse(
                resume.getType(),
                resume.getTexteResume(),
                resume.getPointsCles(),
                resume.getSegmentsSources(),
                resume.getStatut(),
                resume.getDateCreation()
        );
    }
}
