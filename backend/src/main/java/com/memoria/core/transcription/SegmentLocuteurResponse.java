package com.memoria.core.transcription;

public record SegmentLocuteurResponse(int locuteur, String texte, long offsetMillisecondes, long dureeMillisecondes) {

    public static SegmentLocuteurResponse depuis(SegmentLocuteur segment) {
        return new SegmentLocuteurResponse(
                segment.getLocuteur(),
                segment.getTexte(),
                segment.getOffsetMillisecondes(),
                segment.getDureeMillisecondes()
        );
    }
}
