package com.memoria.entreprise.engagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EngagementRepository extends JpaRepository<Engagement, UUID> {

    boolean existsBySessionId(UUID sessionId);

    List<Engagement> findBySessionIdOrderByDateCreationAsc(UUID sessionId);

    List<Engagement> findAllByOrderByDateCreationDesc();

    List<Engagement> findByStatutOrderByDateCreationDesc(StatutEngagement statut);

    List<Engagement> findByStatutAndDateEcheanceNotNull(StatutEngagement statut);

    List<Engagement> findByResponsableUtilisateurId(UUID responsableUtilisateurId);

    void deleteBySessionId(UUID sessionId);

    // Droit a l'effacement (voir GouvernanceDonneesService) : anonymise
    // l'assignation sans supprimer l'engagement lui-meme, qui reste une
    // donnee partagee (suivi d'action pour toute l'equipe).
    @Modifying
    @Query("update Engagement e set e.responsableUtilisateurId = null where e.responsableUtilisateurId = :utilisateurId")
    void anonymiserResponsable(@Param("utilisateurId") UUID utilisateurId);
}
