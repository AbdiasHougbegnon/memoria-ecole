package com.memoria.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

    Optional<Utilisateur> findByEmail(String email);

    // Insensible a la casse : utilise pour la promotion admin (AdminBootstrapRunner,
    // GouvernanceDonneesService.resoudreParEmail) ou l'email saisi peut ne pas
    // reprendre exactement la casse d'inscription -- findByEmail reste exact
    // pour connecter()/inscrire(), comportement historique inchange.
    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);
}
