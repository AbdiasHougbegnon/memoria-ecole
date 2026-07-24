package com.memoria.ecole.notion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

// Une ligne par (notion, etudiant) -- meme style que MembreCouloir
// (couloir_id, utilisateur_id).
@Entity
@Table(name = "maitrises_notions", uniqueConstraints = @UniqueConstraint(columnNames = {"notion_id", "utilisateur_id"}))
public class MaitriseNotion {

    @Id
    private UUID id;

    @Column(name = "notion_id", nullable = false)
    private UUID notionId;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiveauMaitrise niveau;

    @Column(name = "nombre_tentatives", nullable = false)
    private int nombreTentatives;

    @Column(name = "date_mise_a_jour", nullable = false)
    private Instant dateMiseAJour;

    protected MaitriseNotion() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public MaitriseNotion(UUID notionId, UUID utilisateurId) {
        this.id = UUID.randomUUID();
        this.notionId = notionId;
        this.utilisateurId = utilisateurId;
        this.niveau = NiveauMaitrise.NON_ABORDEE;
        this.nombreTentatives = 0;
        this.dateMiseAJour = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getNotionId() {
        return notionId;
    }

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public NiveauMaitrise getNiveau() {
        return niveau;
    }

    public int getNombreTentatives() {
        return nombreTentatives;
    }

    public Instant getDateMiseAJour() {
        return dateMiseAJour;
    }

    public void mettreAJour(NiveauMaitrise nouveauNiveau) {
        this.niveau = nouveauNiveau;
        this.nombreTentatives++;
        this.dateMiseAJour = Instant.now();
    }
}
