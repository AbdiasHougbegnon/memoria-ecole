package com.memoria.core.couloir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CouloirRepository extends JpaRepository<Couloir, UUID> {

    // Droit a l'effacement (voir GouvernanceDonneesService) : couloirs dont
    // l'utilisateur efface est proprietaire, a transferer ou supprimer.
    List<Couloir> findByProprietaireId(UUID proprietaireId);
}
