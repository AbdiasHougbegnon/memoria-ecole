package com.memoria.ecole.tuteurvocal;

import java.util.UUID;

// L'utilisateur authentifie n'est pas l'etudiant proprietaire de cette
// seance de tutorat (chaque SeanceTutorat est personnelle, contrairement au
// couloir qui est partage).
public class AccesTutoratRefuseException extends RuntimeException {

    public AccesTutoratRefuseException(UUID seanceTutoratId, UUID utilisateurId) {
        super("L'utilisateur " + utilisateurId + " n'a pas acces a la seance de tutorat " + seanceTutoratId);
    }
}
