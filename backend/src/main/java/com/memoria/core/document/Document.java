package com.memoria.core.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDocument type;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "chemin_stockage", nullable = false)
    private String cheminStockage;

    @Column(name = "texte_extrait", columnDefinition = "text")
    private String texteExtrait;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDocument statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Document() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Document(UUID sessionId, TypeDocument type, String nomFichier, String cheminStockage) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.type = type;
        this.nomFichier = nomFichier;
        this.cheminStockage = cheminStockage;
        this.statut = StatutDocument.EN_ATTENTE;
        this.dateCreation = Instant.now();
    }

    public void marquerReussi(String texteExtrait) {
        this.texteExtrait = texteExtrait;
        this.statut = StatutDocument.REUSSI;
    }

    public void marquerEchec() {
        this.statut = StatutDocument.ECHEC;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public TypeDocument getType() {
        return type;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getCheminStockage() {
        return cheminStockage;
    }

    public String getTexteExtrait() {
        return texteExtrait;
    }

    public StatutDocument getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
