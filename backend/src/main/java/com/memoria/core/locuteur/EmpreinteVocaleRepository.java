package com.memoria.core.locuteur;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpreinteVocaleRepository extends JpaRepository<EmpreinteVocale, UUID> {

    Optional<EmpreinteVocale> findByUtilisateurId(UUID utilisateurId);

    Optional<EmpreinteVocale> findByProfilExterneId(String profilExterneId);

    List<EmpreinteVocale> findByStatut(StatutEmpreinteVocale statut);
}
