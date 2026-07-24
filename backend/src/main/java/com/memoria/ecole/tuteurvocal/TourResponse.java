package com.memoria.ecole.tuteurvocal;

import java.time.Instant;
import java.util.UUID;

public record TourResponse(UUID id, Locuteur locuteur, String texte, Instant dateCreation, String audioUrl) {

    public static TourResponse depuis(TourDialogueTutorat tour) {
        String audioUrl = tour.getLocuteur() == Locuteur.TUTEUR
                ? "/api/v1/tutorat/" + tour.getSeanceTutoratId() + "/audio/" + tour.getId()
                : null;
        return new TourResponse(tour.getId(), tour.getLocuteur(), tour.getTexte(), tour.getDateCreation(), audioUrl);
    }
}
