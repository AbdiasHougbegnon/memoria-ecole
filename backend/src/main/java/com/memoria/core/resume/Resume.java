package com.memoria.core.resume;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "texte_resume", columnDefinition = "text")
    private String texteResume;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_points_cles", joinColumns = @JoinColumn(name = "resume_id"))
    @OrderColumn(name = "position")
    @Column(name = "point_cle", columnDefinition = "text")
    private List<String> pointsCles;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_segments_sources", joinColumns = @JoinColumn(name = "resume_id"))
    @Column(name = "numero_sequence")
    private List<Integer> segmentsSources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResumeStatut statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Resume() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Resume(
            UUID sessionId,
            String texteResume,
            List<String> pointsCles,
            List<Integer> segmentsSources,
            ResumeStatut statut
    ) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.texteResume = texteResume;
        this.pointsCles = pointsCles;
        this.segmentsSources = segmentsSources;
        this.statut = statut;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getTexteResume() {
        return texteResume;
    }

    public List<String> getPointsCles() {
        return pointsCles;
    }

    public List<Integer> getSegmentsSources() {
        return segmentsSources;
    }

    public ResumeStatut getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
