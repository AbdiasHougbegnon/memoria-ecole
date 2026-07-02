package com.memoria.core.audio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "audio_chunks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "numero_sequence"})
)
public class AudioChunk {

    @jakarta.persistence.Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "numero_sequence", nullable = false)
    private int numeroSequence;

    @Column(name = "chemin_stockage", nullable = false)
    private String cheminStockage;

    @Column(name = "taille_octets", nullable = false)
    private long tailleOctets;

    @Column(name = "date_reception", nullable = false)
    private Instant dateReception;

    protected AudioChunk() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public AudioChunk(UUID sessionId, int numeroSequence, String cheminStockage, long tailleOctets) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.numeroSequence = numeroSequence;
        this.cheminStockage = cheminStockage;
        this.tailleOctets = tailleOctets;
        this.dateReception = Instant.now();
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

    public String getCheminStockage() {
        return cheminStockage;
    }

    public long getTailleOctets() {
        return tailleOctets;
    }

    public Instant getDateReception() {
        return dateReception;
    }
}
