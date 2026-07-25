package com.memoria.core.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    Optional<Resume> findBySessionIdAndType(UUID sessionId, ResumeType type);

    // Export des donnees (voir GouvernanceDonneesService) : tous les resumes
    // d'une session, quel que soit le type.
    List<Resume> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
