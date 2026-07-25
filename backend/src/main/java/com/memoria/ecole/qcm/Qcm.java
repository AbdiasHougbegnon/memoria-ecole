package com.memoria.ecole.qcm;

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

// Un seul QCM par session (comme ResumeCours) : la contrainte d'unicite ne
// porte que sur session_id. Genere a partir du ResumeCours de la session, pas
// de la transcription brute -- voir QcmService.
@Entity
@Table(name = "qcms")
public class Qcm {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "qcm_questions", joinColumns = @JoinColumn(name = "qcm_id"))
    @OrderColumn(name = "position")
    private List<QuestionQcm> questions;

    // Memes segmentsSources que le ResumeCours source (traçabilite en chaine :
    // QCM -> resume de cours -> segments -> transcription -> audio), pas de
    // traçabilite par question individuelle -- meme compromis que ResumeCours.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "qcm_segments_sources", joinColumns = @JoinColumn(name = "qcm_id"))
    @Column(name = "numero_sequence")
    private List<Integer> segmentsSources;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutQcm statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected Qcm() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public Qcm(UUID sessionId, List<QuestionQcm> questions, List<Integer> segmentsSources, StatutQcm statut) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.questions = questions;
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

    public List<QuestionQcm> getQuestions() {
        return questions;
    }

    public List<Integer> getSegmentsSources() {
        return segmentsSources;
    }

    public StatutQcm getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
