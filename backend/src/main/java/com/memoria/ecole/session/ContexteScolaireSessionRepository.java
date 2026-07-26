package com.memoria.ecole.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContexteScolaireSessionRepository extends JpaRepository<ContexteScolaireSession, UUID> {

    Optional<ContexteScolaireSession> findBySessionId(UUID sessionId);
}
