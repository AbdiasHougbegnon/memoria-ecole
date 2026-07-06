package com.memoria.core.recherche;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IndexRechercheRepository extends JpaRepository<IndexRecherche, UUID> {

    Optional<IndexRecherche> findBySessionId(UUID sessionId);
}
