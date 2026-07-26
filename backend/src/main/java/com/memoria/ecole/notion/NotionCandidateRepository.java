package com.memoria.ecole.notion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotionCandidateRepository extends JpaRepository<NotionCandidate, UUID> {

    List<NotionCandidate> findByMatiereIdOrderByDateCreationAsc(UUID matiereId);
}
