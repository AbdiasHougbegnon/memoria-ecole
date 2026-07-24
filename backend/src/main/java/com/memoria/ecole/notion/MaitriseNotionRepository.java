package com.memoria.ecole.notion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaitriseNotionRepository extends JpaRepository<MaitriseNotion, UUID> {

    Optional<MaitriseNotion> findByNotionIdAndUtilisateurId(UUID notionId, UUID utilisateurId);

    List<MaitriseNotion> findByUtilisateurIdAndNotionIdIn(UUID utilisateurId, List<UUID> notionIds);
}
