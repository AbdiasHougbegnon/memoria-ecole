package com.memoria.ecole.qcm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QcmMatiereRepository extends JpaRepository<QcmMatiere, UUID> {

    Optional<QcmMatiere> findByMatiereId(UUID matiereId);
}
