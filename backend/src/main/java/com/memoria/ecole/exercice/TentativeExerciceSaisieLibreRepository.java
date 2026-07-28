package com.memoria.ecole.exercice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TentativeExerciceSaisieLibreRepository extends JpaRepository<TentativeExerciceSaisieLibre, UUID> {

    Optional<TentativeExerciceSaisieLibre> findByExerciceMatiereIdAndUtilisateurId(UUID exerciceMatiereId, UUID utilisateurId);
}
