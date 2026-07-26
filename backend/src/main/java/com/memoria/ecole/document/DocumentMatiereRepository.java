package com.memoria.ecole.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentMatiereRepository extends JpaRepository<DocumentMatiere, UUID> {

    List<DocumentMatiere> findByMatiereIdOrderByDateCreationAsc(UUID matiereId);
}
