package com.memoria.ecole.exercice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExercicePapierRepository extends JpaRepository<ExercicePapier, UUID> {

    List<ExercicePapier> findByTravailPapierIdOrderByOrdreAsc(UUID travailPapierId);

    void deleteByTravailPapierId(UUID travailPapierId);
}
