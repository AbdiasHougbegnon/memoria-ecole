package com.memoria.entreprise.compterendu;

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

// Contrairement a Resume (plusieurs types possibles par session), il n'y a
// qu'un seul compte rendu complet par session : la contrainte d'unicite ne
// porte que sur session_id.
@Entity
@Table(name = "comptes_rendus")
public class CompteRendu {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(columnDefinition = "text")
    private String synthese;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "compte_rendu_decisions", joinColumns = @JoinColumn(name = "compte_rendu_id"))
    @OrderColumn(name = "position")
    @Column(name = "decision", columnDefinition = "text")
    private List<String> decisions;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "compte_rendu_actions", joinColumns = @JoinColumn(name = "compte_rendu_id"))
    @OrderColumn(name = "position")
    private List<ActionCompteRendu> actions;

    // Meme principe que Resume.segmentsSources : les numeros de sequence des
    // chunks transcrits ayant servi a la generation (tracabilite au niveau
    // du document, pas par decision/action individuelle -- coherent avec ce
    // qui existe deja pour les resumes).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "compte_rendu_segments_sources", joinColumns = @JoinColumn(name = "compte_rendu_id"))
    @Column(name = "numero_sequence")
    private List<Integer> segmentsSources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCompteRendu statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected CompteRendu() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public CompteRendu(
            UUID sessionId,
            String synthese,
            List<String> decisions,
            List<ActionCompteRendu> actions,
            List<Integer> segmentsSources,
            StatutCompteRendu statut
    ) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.synthese = synthese;
        this.decisions = decisions;
        this.actions = actions;
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

    public List<String> getDecisions() {
        return decisions;
    }

    public List<ActionCompteRendu> getActions() {
        return actions;
    }

    public List<Integer> getSegmentsSources() {
        return segmentsSources;
    }

    public StatutCompteRendu getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
