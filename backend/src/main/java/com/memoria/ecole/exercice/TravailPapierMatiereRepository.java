package com.memoria.ecole.exercice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TravailPapierMatiereRepository extends JpaRepository<TravailPapierMatiere, UUID> {

    List<TravailPapierMatiere> findByMatiereIdAndUtilisateurIdOrderByDateCreationDesc(UUID matiereId, UUID utilisateurId);
}
