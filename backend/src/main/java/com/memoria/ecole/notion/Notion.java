package com.memoria.ecole.notion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Entite de premier ordre (contrairement a NotionCours, un @Embeddable sans
// id ni suivi de maitrise, cf. ecole.resumecours) : une Notion appartient a
// une Matiere et peut etre suivie individuellement par etudiant via
// MaitriseNotion, et reutilisee par plusieurs Seances (SeanceNotion).
@Entity
@Table(name = "notions")
public class Notion {

    @Id
    private UUID id;

    @Column(name = "matiere_id", nullable = false)
    private UUID matiereId;

    @Column(nullable = false)
    private String terme;

    @Column(columnDefinition = "text", nullable = false)
    private String definition;

    @Column(nullable = false)
    private int ordre;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Notion() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Notion(UUID matiereId, String terme, String definition, int ordre) {
        this.id = UUID.randomUUID();
        this.matiereId = matiereId;
        this.terme = terme;
        this.definition = definition;
        this.ordre = ordre;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatiereId() {
        return matiereId;
    }

    public String getTerme() {
        return terme;
    }

    public String getDefinition() {
        return definition;
    }

    public int getOrdre() {
        return ordre;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
