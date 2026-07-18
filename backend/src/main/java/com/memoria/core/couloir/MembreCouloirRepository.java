package com.memoria.core.couloir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MembreCouloirRepository extends JpaRepository<MembreCouloir, UUID> {

    boolean existsByCouloirIdAndUtilisateurId(UUID couloirId, UUID utilisateurId);

    List<MembreCouloir> findByUtilisateurId(UUID utilisateurId);

    long countByCouloirId(UUID couloirId);
}
