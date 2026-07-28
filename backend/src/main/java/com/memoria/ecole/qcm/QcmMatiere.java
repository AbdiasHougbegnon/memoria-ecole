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

// QCM de revision base sur toute la matiere (phase 22c), distinct de Qcm
// (une session precise) : les deux coexistent, l'un pour reviser un cours en
// particulier, l'autre pour une revision progressive sur l'ensemble de la
// matiere. Un seul par matiere (comme Qcm par session) -- regenere via un
// nouvel appel explicite si le contenu de la matiere a change entre-temps
// (pas de regeneration automatique).
// Pas de segmentsSources ici (contrairement a Qcm) : le contenu source
// s'etend potentiellement sur plusieurs sessions et documents, une simple
// liste de numeros de sequence ne suffit plus a le representer -- limite
// assumee, voir docs/phases/phase-22-tutorat-progressif.md.
@Entity
@Table(name = "qcm_matieres")
public class QcmMatiere {

    @Id
    private UUID id;

    @Column(name = "matiere_id", nullable = false, unique = true)
    private UUID matiereId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "qcm_matiere_questions", joinColumns = @JoinColumn(name = "qcm_matiere_id"))
    @OrderColumn(name = "position")
    private List<QuestionQcm> questions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutQcm statut;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    protected QcmMatiere() {
        // constructeur requis par Hibernate, ne pas utiliser directement
    }

    public QcmMatiere(UUID matiereId, List<QuestionQcm> questions, StatutQcm statut) {
        this.id = UUID.randomUUID();
        this.matiereId = matiereId;
        this.questions = questions;
        this.statut = statut;
        this.dateCreation = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatiereId() {
        return matiereId;
    }

    public List<QuestionQcm> getQuestions() {
        return questions;
    }

    public StatutQcm getStatut() {
        return statut;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }
}
