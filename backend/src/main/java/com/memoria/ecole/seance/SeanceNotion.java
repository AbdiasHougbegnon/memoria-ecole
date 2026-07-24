package com.memoria.ecole.seance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

// Quelles notions sont couvertes par quelle seance, et dans quel ordre --
// meme style de jointure plate que MembreCouloir (pas de @ManyToMany).
@Entity
@Table(name = "seance_notions", uniqueConstraints = @UniqueConstraint(columnNames = {"seance_id", "notion_id"}))
public class SeanceNotion {

    @Id
    private UUID id;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Column(name = "notion_id", nullable = false)
    private UUID notionId;

    @Column(nullable = false)
    private int ordre;

    protected SeanceNotion() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public SeanceNotion(UUID seanceId, UUID notionId, int ordre) {
        this.id = UUID.randomUUID();
        this.seanceId = seanceId;
        this.notionId = notionId;
        this.ordre = ordre;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSeanceId() {
        return seanceId;
    }

    public UUID getNotionId() {
        return notionId;
    }

    public int getOrdre() {
        return ordre;
    }
}
