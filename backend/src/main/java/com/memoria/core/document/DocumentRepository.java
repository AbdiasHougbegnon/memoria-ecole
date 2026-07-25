package com.memoria.core.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findBySessionIdOrderByDateCreationAsc(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
