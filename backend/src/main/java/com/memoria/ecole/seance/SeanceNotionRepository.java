package com.memoria.ecole.seance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeanceNotionRepository extends JpaRepository<SeanceNotion, UUID> {

    List<SeanceNotion> findBySeanceIdOrderByOrdreAsc(UUID seanceId);

    void deleteBySeanceId(UUID seanceId);

    void deleteByNotionId(UUID notionId);
}
