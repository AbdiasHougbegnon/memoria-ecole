package com.memoria.ecole.exercice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

// Quelles notions un ExerciceMatiere donne couvre -- meme role que
// com.memoria.ecole.qcm.QcmMatiereNotion pour le QCM, meme style de jointure
// plate que SeanceNotion (pas de @ManyToMany). Permet a
// ExerciceSaisieLibreService de savoir si des exercices deja persistes
// correspondent exactement a la selection de notions demandee, pour
// reutiliser sans regenerer quand la selection est inchangee.
@Entity
@Table(name = "exercice_matiere_notions", uniqueConstraints = @UniqueConstraint(columnNames = {"exercice_matiere_id", "notion_id"}))
public class ExerciceMatiereNotion {

    @Id
    private UUID id;

    @Column(name = "exercice_matiere_id", nullable = false)
    private UUID exerciceMatiereId;

    @Column(name = "notion_id", nullable = false)
    private UUID notionId;

    protected ExerciceMatiereNotion() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ExerciceMatiereNotion(UUID exerciceMatiereId, UUID notionId) {
        this.id = UUID.randomUUID();
        this.exerciceMatiereId = exerciceMatiereId;
        this.notionId = notionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciceMatiereId() {
        return exerciceMatiereId;
    }

    public UUID getNotionId() {
        return notionId;
    }
}
