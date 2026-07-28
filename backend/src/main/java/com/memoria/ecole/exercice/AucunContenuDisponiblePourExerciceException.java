package com.memoria.ecole.exercice;

import java.util.UUID;

// Meme raisonnement que AucunContenuMatiereDisponibleException (ecole.qcm) :
// les exercices se generent a partir du contenu deja agrege de la matiere
// (voir AgregateurContenuMatiereService), il en faut au moins un peu.
public class AucunContenuDisponiblePourExerciceException extends RuntimeException {

    public AucunContenuDisponiblePourExerciceException(UUID matiereId) {
        super("Aucun contenu disponible pour la matiere " + matiereId + " : rien pour generer des exercices");
    }
}
