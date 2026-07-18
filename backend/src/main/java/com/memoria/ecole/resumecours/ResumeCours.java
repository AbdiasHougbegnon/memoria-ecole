package com.memoria.ecole.resumecours;

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

// Un seul resume de cours par session (comme CompteRendu cote Entreprise) :
// la contrainte d'unicite ne porte que sur session_id.
@Entity
@Table(name = "resumes_cours")
public class ResumeCours {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(columnDefinition = "text")
    private String synthese;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_cours_notions", joinColumns = @JoinColumn(name = "resume_cours_id"))
    @OrderColumn(name = "position")
    private List<NotionCours> notions;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_cours_points_a_revoir", joinColumns = @JoinColumn(name = "resume_cours_id"))
    @OrderColumn(name = "position")
    @Column(name = "point", columnDefinition = "text")
    private List<String> pointsARevoir;

    // Meme principe que Resume.segmentsSources et CompteRendu.segmentsSources :
    // tracabilite au niveau du document (numeros de sequence des chunks
    // transcrits utilises), pas par notion individuelle.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_cours_segments_sources", joinColumns = @JoinColumn(name = "resume_cours_id"))
    @Column(name = "numero_sequence")
    private List<Integer> segmentsSources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutResumeCours statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected ResumeCours() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public ResumeCours(
            UUID sessionId,
            String synthese,
            List<NotionCours> notions,
            List<String> pointsARevoir,
            List<Integer> segmentsSources,
            StatutResumeCours statut
    ) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.synthese = synthese;
        this.notions = notions;
        this.pointsARevoir = pointsARevoir;
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

    public String getSynthese() {
        return synthese;
    }

    public List<NotionCours> getNotions() {
        return notions;
    }

    public List<String> getPointsARevoir() {
        return pointsARevoir;
    }

    public List<Integer> getSegmentsSources() {
        return segmentsSources;
    }

    public StatutResumeCours getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
