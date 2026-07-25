package com.memoria.ecole.notion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaitriseNotionRepository extends JpaRepository<MaitriseNotion, UUID> {

    Optional<MaitriseNotion> findByNotionIdAndUtilisateurId(UUID notionId, UUID utilisateurId);

    List<MaitriseNotion> findByUtilisateurIdAndNotionIdIn(UUID utilisateurId, List<UUID> notionIds);

    // Droit a l'effacement / export (voir GouvernanceDonneesService) :
    // progression personnelle, hors de propos pour les autres membres du
    // couloir meme si la notion sous-jacente est partagee.
    List<MaitriseNotion> findByUtilisateurId(UUID utilisateurId);

    void deleteByUtilisateurId(UUID utilisateurId);
}
