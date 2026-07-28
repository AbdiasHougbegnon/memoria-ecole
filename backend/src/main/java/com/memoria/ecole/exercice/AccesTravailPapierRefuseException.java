package com.memoria.ecole.exercice;

import java.util.UUID;

// Chaque travail papier est personnel a l'etudiant qui l'a soumis (voir
// TravailPapierMatiere) -- meme doctrine que AccesTutoratRefuseException.
public class AccesTravailPapierRefuseException extends RuntimeException {

    public AccesTravailPapierRefuseException(UUID travailId, UUID utilisateurId) {
        super("L'utilisateur " + utilisateurId + " n'a pas acces au travail papier " + travailId);
    }
}
