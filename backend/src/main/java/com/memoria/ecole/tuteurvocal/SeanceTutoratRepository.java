package com.memoria.ecole.tuteurvocal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SeanceTutoratRepository extends JpaRepository<SeanceTutorat, UUID> {

    Optional<SeanceTutorat> findBySeanceIdAndUtilisateurIdAndStatut(UUID seanceId, UUID utilisateurId, StatutSeanceTutorat statut);
}
