package com.memoria.ecole.exercice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciceMatiereNotionRepository extends JpaRepository<ExerciceMatiereNotion, UUID> {

    List<ExerciceMatiereNotion> findByExerciceMatiereId(UUID exerciceMatiereId);

    void deleteByExerciceMatiereId(UUID exerciceMatiereId);
}
