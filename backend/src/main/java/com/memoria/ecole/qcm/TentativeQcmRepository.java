package com.memoria.ecole.qcm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TentativeQcmRepository extends JpaRepository<TentativeQcm, UUID> {

    Optional<TentativeQcm> findByQcmIdAndUtilisateurId(UUID qcmId, UUID utilisateurId);

    // Droit a l'effacement / export (voir GouvernanceDonneesService) : progression
    // personnelle, hors de propos pour les autres utilisateurs meme si le QCM
    // sous-jacent est partage.
    List<TentativeQcm> findByUtilisateurId(UUID utilisateurId);

    void deleteByUtilisateurId(UUID utilisateurId);

    // Purge de session (voir SessionPurgeService) : le QCM n'a pas de FK JPA
    // vers ses tentatives, nettoyage explicite requis avant de supprimer le QCM.
    void deleteByQcmId(UUID qcmId);
}
