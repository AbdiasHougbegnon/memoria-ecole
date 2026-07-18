package com.memoria.core.couloir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Concept de moteur generique (pas Ecole ni Entreprise) : le master prompt
// decrit le "proprietaire de couloir" comme un role applicable a "un espace
// de classe ou d'equipe" -- reutilisable par les deux produits.
@Entity
@Table(name = "couloirs")
public class Couloir {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(name = "proprietaire_id", nullable = false)
    private UUID proprietaireId;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Couloir() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Couloir(String nom, UUID proprietaireId) {
        this.id = UUID.randomUUID();
        this.nom = nom;
        this.proprietaireId = proprietaireId;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public UUID getProprietaireId() {
        return proprietaireId;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
