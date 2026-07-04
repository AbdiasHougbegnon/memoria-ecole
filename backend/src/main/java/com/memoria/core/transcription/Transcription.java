package com.memoria.core.transcription;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "transcriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "numero_sequence"})
)
public class Transcription {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "numero_sequence", nullable = false)
    private int numeroSequence;

    @Column(columnDefinition = "text")
    private String texte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranscriptionStatut statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "transcription_segments_locuteur", joinColumns = @JoinColumn(name = "transcription_id"))
    @OrderColumn(name = "position")
    private List<SegmentLocuteur> segmentsLocuteur;

    protected Transcription() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Transcription(UUID sessionId, int numeroSequence, String texte, TranscriptionStatut statut) {
        this(sessionId, numeroSequence, texte, statut, List.of());
    }

    public Transcription(
            UUID sessionId,
            int numeroSequence,
            String texte,
            TranscriptionStatut statut,
            List<SegmentLocuteur> segmentsLocuteur
    ) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.numeroSequence = numeroSequence;
        this.texte = texte;
        this.statut = statut;
        this.dateCreation = Instant.now();
        this.segmentsLocuteur = segmentsLocuteur;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getNumeroSequence() {
        return numeroSequence;
    }

    public String getTexte() {
        return texte;
    }

    public TranscriptionStatut getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public List<SegmentLocuteur> getSegmentsLocuteur() {
        return segmentsLocuteur;
    }
}
