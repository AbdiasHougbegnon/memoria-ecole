package com.memoria.ecole.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContexteScolaireSessionRepository extends JpaRepository<ContexteScolaireSession, UUID> {

    Optional<ContexteScolaireSession> findBySessionId(UUID sessionId);

    // Phase 22c : toutes les sessions rattachees a une matiere, pour agreger
    // leurs resumes de cours dans le QCM/tuteur de la matiere entiere.
    List<ContexteScolaireSession> findByMatiereId(UUID matiereId);
}
