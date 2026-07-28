package com.memoria.ecole.exercice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExerciceMatiereRepository extends JpaRepository<ExerciceMatiere, UUID> {

    Optional<ExerciceMatiere> findByMatiereId(UUID matiereId);
}
