package com.memoria.ecole.resumecours;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ResumeCoursRepository extends JpaRepository<ResumeCours, UUID> {

    Optional<ResumeCours> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
