package com.memoria.ecole.qcm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

// Quelles notions un QcmMatiere donne couvre -- meme style de jointure plate
// que SeanceNotion (pas de @ManyToMany). Permet a QcmMatiereService de savoir
// si un QCM deja persiste correspond exactement a la selection de notions
// demandee (voir obtenirOuGenererQcmMatiere), pour reutiliser sans regenerer
// quand la selection est inchangee.
@Entity
@Table(name = "qcm_matiere_notions", uniqueConstraints = @UniqueConstraint(columnNames = {"qcm_matiere_id", "notion_id"}))
public class QcmMatiereNotion {

    @Id
    private UUID id;

    @Column(name = "qcm_matiere_id", nullable = false)
    private UUID qcmMatiereId;

    @Column(name = "notion_id", nullable = false)
    private UUID notionId;

    protected QcmMatiereNotion() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public QcmMatiereNotion(UUID qcmMatiereId, UUID notionId) {
        this.id = UUID.randomUUID();
        this.qcmMatiereId = qcmMatiereId;
        this.notionId = notionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQcmMatiereId() {
        return qcmMatiereId;
    }

    public UUID getNotionId() {
        return notionId;
    }
}
