package com.memoria.ecole.matiere;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatiereRepository extends JpaRepository<Matiere, UUID> {

    List<Matiere> findByCouloirId(UUID couloirId);
}
