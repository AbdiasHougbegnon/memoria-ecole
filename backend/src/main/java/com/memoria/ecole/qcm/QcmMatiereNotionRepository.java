package com.memoria.ecole.qcm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QcmMatiereNotionRepository extends JpaRepository<QcmMatiereNotion, UUID> {

    List<QcmMatiereNotion> findByQcmMatiereId(UUID qcmMatiereId);

    void deleteByQcmMatiereId(UUID qcmMatiereId);
}
