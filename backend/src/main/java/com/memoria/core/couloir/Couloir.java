package com.memoria.core.couloir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// proprietaireId n'est plus un role : tous les membres d'un couloir ont les
// memes droits (voir CouloirService.verifierMembre). Le champ est conserve
// uniquement comme metadonnee "cree par", nullable pour pouvoir etre
// anonymise a la suppression du compte createur (meme pattern que
// Session.createurId, voir anonymiserProprietaire).
@Entity
@Table(name = "couloirs")
public class Couloir {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(name = "proprietaire_id")
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

    public void renommer(String nouveauNom) {
        this.nom = nouveauNom;
    }

    public void anonymiserProprietaire() {
        this.proprietaireId = null;
    }
}
