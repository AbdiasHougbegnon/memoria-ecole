package com.memoria.ecole.seance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeanceRepository extends JpaRepository<Seance, UUID> {

    List<Seance> findByMatiereId(UUID matiereId);
}
