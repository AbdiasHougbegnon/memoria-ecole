package com.memoria.core.couloir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MembreCouloirRepository extends JpaRepository<MembreCouloir, UUID> {

    boolean existsByCouloirIdAndUtilisateurId(UUID couloirId, UUID utilisateurId);

    List<MembreCouloir> findByUtilisateurId(UUID utilisateurId);

    List<MembreCouloir> findByCouloirId(UUID couloirId);

    long countByCouloirId(UUID couloirId);

    void deleteByCouloirId(UUID couloirId);

    void deleteByCouloirIdAndUtilisateurId(UUID couloirId, UUID utilisateurId);

    // Droit a l'effacement (voir GouvernanceDonneesService) : quitte tous les
    // couloirs restants (les couloirs dont l'utilisateur est proprietaire
    // sont deja transferes/supprimes avant cet appel).
    void deleteByUtilisateurId(UUID utilisateurId);
}
