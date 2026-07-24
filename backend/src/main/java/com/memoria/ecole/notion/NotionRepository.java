package com.memoria.ecole.notion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotionRepository extends JpaRepository<Notion, UUID> {

    List<Notion> findByMatiereIdOrderByOrdreAsc(UUID matiereId);
}
