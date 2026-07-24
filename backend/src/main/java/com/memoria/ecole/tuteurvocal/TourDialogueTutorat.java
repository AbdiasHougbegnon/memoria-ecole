package com.memoria.ecole.tuteurvocal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Pas de stockage audio brut : seul le texte est persiste, l'audio est
// resynthetise a la demande (endpoint dedie) -- coherent avec l'absence de
// stockage de blobs ailleurs dans le projet en dehors du cas deja gere
// (fichiers audio de session), voir docs/phases/phase-9-tuteur-vocal.md.
@Entity
@Table(name = "tours_dialogue_tutorat")
public class TourDialogueTutorat {

    @Id
    private UUID id;

    @Column(name = "seance_tutorat_id", nullable = false)
    private UUID seanceTutoratId;

    @Column(name = "notion_id", nullable = false)
    private UUID notionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Locuteur locuteur;

    @Column(columnDefinition = "text", nullable = false)
    private String texte;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected TourDialogueTutorat() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public TourDialogueTutorat(UUID seanceTutoratId, UUID notionId, Locuteur locuteur, String texte) {
        this.id = UUID.randomUUID();
        this.seanceTutoratId = seanceTutoratId;
        this.notionId = notionId;
        this.locuteur = locuteur;
        this.texte = texte;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSeanceTutoratId() {
        return seanceTutoratId;
    }

    public UUID getNotionId() {
        return notionId;
    }

    public Locuteur getLocuteur() {
        return locuteur;
    }

    public String getTexte() {
        return texte;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
