package com.memoria.core.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findAllByOrderByDateCreationDesc();

    List<Session> findByCouloirIdOrderByDateCreationDesc(UUID couloirId);

    // Utilisateur sans couloir : visible si createur, ou session "ancienne"
    // (creee avant l'introduction de createurId).
    List<Session> findByCreateurIdOrCreateurIdIsNullOrderByDateCreationDesc(UUID createurId);

    // Utilisateur avec des couloirs : ajoute les sessions rattachees a l'un
    // de ses couloirs. Methode separee plutot qu'un IN() toujours utilise,
    // car un IN() avec une liste vide n'est pas valide en JPQL/SQL.
    @Query("SELECT s FROM Session s WHERE s.createurId = :utilisateurId OR s.createurId IS NULL OR s.couloirId IN :couloirIds ORDER BY s.dateCreation DESC")
    List<Session> findVisiblesPour(@Param("utilisateurId") UUID utilisateurId, @Param("couloirIds") List<UUID> couloirIds);

    // Droit a l'effacement (voir GouvernanceDonneesService) : sessions creees
    // par l'utilisateur, personnelles ou rattachees a un couloir.
    List<Session> findByCreateurId(UUID createurId);

    // Purge de retention (voir RetentionService) : toutes les sessions plus
    // vieilles que le seuil configure, sans distinction personnelle/partagee.
    List<Session> findByDateCreationBefore(Instant seuil);
}
