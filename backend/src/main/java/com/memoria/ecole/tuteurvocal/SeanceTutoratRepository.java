package com.memoria.ecole.tuteurvocal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeanceTutoratRepository extends JpaRepository<SeanceTutorat, UUID> {

    Optional<SeanceTutorat> findBySeanceIdAndUtilisateurIdAndStatut(UUID seanceId, UUID utilisateurId, StatutSeanceTutorat statut);

    // Droit a l'effacement (voir GouvernanceDonneesService) : donnee
    // personnelle exclusive ("chaque etudiant garde un espace personnel
    // prive... ses revisions avec le tuteur vocal", master prompt).
    List<SeanceTutorat> findByUtilisateurId(UUID utilisateurId);
}
