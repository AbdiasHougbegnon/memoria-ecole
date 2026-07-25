package com.memoria.ecole.qcm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QcmRepository extends JpaRepository<Qcm, UUID> {

    Optional<Qcm> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
