package com.memoria.ecole.notion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Proposition de Notion extraite par IA a partir d'un DocumentMatiere,
// jamais injectee directement dans le suivi de maitrise reel des etudiants :
// une extraction IA peut halluciner, la validation enseignant (transformation
// en vraie Notion via NotionService.creerNotionValidee) est obligatoire.
@Entity
@Table(name = "notions_candidates")
public class NotionCandidate {

    @Id
    private UUID id;

    @Column(name = "document_matiere_id", nullable = false)
    private UUID documentMatiereId;

    @Column(name = "matiere_id", nullable = false)
    private UUID matiereId;

    @Column(nullable = false)
    private String terme;

    @Column(columnDefinition = "text", nullable = false)
    private String definition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutNotionCandidate statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected NotionCandidate() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public NotionCandidate(UUID documentMatiereId, UUID matiereId, String terme, String definition) {
        this.id = UUID.randomUUID();
        this.documentMatiereId = documentMatiereId;
        this.matiereId = matiereId;
        this.terme = terme;
        this.definition = definition;
        this.statut = StatutNotionCandidate.EN_ATTENTE;
        this.dateCreation = Instant.now();
    }

    public void marquerValidee() {
        this.statut = StatutNotionCandidate.VALIDEE;
    }

    public void marquerRejetee() {
        this.statut = StatutNotionCandidate.REJETEE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentMatiereId() {
        return documentMatiereId;
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

    public StatutNotionCandidate getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
