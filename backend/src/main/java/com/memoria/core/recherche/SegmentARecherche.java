package com.memoria.core.recherche;

public record SegmentARecherche(
        int numeroSequence,
        int locuteur,
        String texte,
        long offsetMillisecondes,
        long dureeMillisecondes
) {
}
