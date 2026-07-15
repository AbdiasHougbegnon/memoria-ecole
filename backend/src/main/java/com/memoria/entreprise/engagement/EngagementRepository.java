package com.memoria.entreprise.engagement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EngagementRepository extends JpaRepository<Engagement, UUID> {

    boolean existsBySessionId(UUID sessionId);

    List<Engagement> findBySessionIdOrderByDateCreationAsc(UUID sessionId);

    List<Engagement> findAllByOrderByDateCreationDesc();

    List<Engagement> findByStatutOrderByDateCreationDesc(StatutEngagement statut);
}
