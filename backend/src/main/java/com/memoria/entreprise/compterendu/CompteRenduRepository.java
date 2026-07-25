package com.memoria.entreprise.compterendu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompteRenduRepository extends JpaRepository<CompteRendu, UUID> {

    Optional<CompteRendu> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
