package com.memoria.core.recherche;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// Une ligne par session, marqueur d'idempotence pour ne jamais reindexer
// (et donc jamais rappeler Azure OpenAI pour des embeddings) une session
// deja traitee -- Azure AI Search lui-meme est l'unique source de verite
// pour le contenu indexe, cette table ne sert qu'a la garde d'idempotence.
@Entity
@Table(name = "index_recherche")
public class IndexRecherche {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "nombre_segments", nullable = false)
    private int nombreSegments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutIndexRecherche statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected IndexRecherche() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public IndexRecherche(UUID sessionId, int nombreSegments, StatutIndexRecherche statut) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.nombreSegments = nombreSegments;
        this.statut = statut;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getNombreSegments() {
        return nombreSegments;
    }

    public StatutIndexRecherche getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
